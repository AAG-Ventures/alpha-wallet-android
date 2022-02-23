package com.alphawallet.app.widget;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.alphawallet.app.R;
import com.alphawallet.app.entity.StandardFunctionInterface;
import com.alphawallet.app.entity.Wallet;
import com.alphawallet.app.repository.EthereumNetworkRepository;
import com.alphawallet.app.service.AWWalletConnectClient;
import com.alphawallet.app.util.Hex;
import com.alphawallet.app.viewmodel.walletconnect.SignMethodDialogViewModel;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.walletconnect.walletconnectv2.client.WalletConnect;

import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED;

public class SignMethodDialog extends BottomSheetDialog
{
    private final FunctionButtonBar functionBar;
    private final TextView dAppName;
    private final ImageView logo;
    private final TextView url;
    private final TextView walletTv;
    private final TextView message;
    private final ImageView networkIcon;
    private final ChainName networkName;
    private final Activity activity;
    private final ImageView closeButton;
    private WalletConnect.Model.SettledSession settledSession;
    private WalletConnect.Model.SessionRequest sessionRequest;
    private String messageTextHex;
    private final String walletAddress;
    private SignMethodDialogViewModel viewModel;

    public SignMethodDialog(@NonNull Activity activity, WalletConnect.Model.SettledSession settledSession, WalletConnect.Model.SessionRequest sessionRequest)
    {
        super(activity, R.style.FullscreenBottomSheetDialogStyle);
        this.activity = activity;
        this.settledSession = settledSession;
        this.sessionRequest = sessionRequest;
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_sign_method, null);
        setContentView(view);
        initViewModel();

        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from((View) view.getParent());
        behavior.setState(STATE_EXPANDED);
        behavior.setSkipCollapsed(true);

        logo = findViewById(R.id.logo);
        dAppName = findViewById(R.id.dapp_name);
        url = findViewById(R.id.url);
        walletTv = findViewById(R.id.wallet);
        message = findViewById(R.id.message);
        networkIcon = findViewById(R.id.network_icon);
        networkName = findViewById(R.id.network_name);
        functionBar = findViewById(R.id.layoutButtons);
        closeButton = findViewById(R.id.image_close);

        List<String> icons = settledSession.getPeerAppMetaData().getIcons();

        if (icons.isEmpty())
        {
            logo.setImageResource(R.drawable.ic_coin_eth_small);
        } else
        {
            Glide.with(activity)
                    .load(icons.get(0))
                    .circleCrop()
                    .into(logo);
        }
        dAppName.setText(settledSession.getPeerAppMetaData().getName());
        url.setText(settledSession.getPeerAppMetaData().getUrl());
        String params = sessionRequest.getRequest().getParams();
        params = params.substring(1);
        params = params.substring(0, params.length() - 1);
        String[] array = params.split(", ");
        walletAddress = array[1];
        walletTv.setText(walletAddress);
        messageTextHex = array[0];
        message.setText(Hex.hexToUtf8(messageTextHex));

        long chainID = Long.parseLong(sessionRequest.getChainId().split(":")[1]);
        networkIcon.setImageResource(EthereumNetworkRepository.getChainLogo(chainID));
        networkName.setChainID(chainID);
        functionBar.setupFunctions(new StandardFunctionInterface()
        {
            @Override
            public void handleClick(String action, int actionId)
            {
                if (actionId == R.string.action_confirm)
                {
                    approve();
                }
            }
        }, Arrays.asList(R.string.action_confirm));

        closeButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Log.d("seaborn", "onClick: close");
                viewModel.reject(sessionRequest);
                dismiss();
            }
        });
    }

    private void initViewModel()
    {
        viewModel = new ViewModelProvider((ViewModelStoreOwner) activity).get(SignMethodDialogViewModel.class);
    }

    private void approve()
    {
        AWWalletConnectClient.viewModel = viewModel;
        viewModel.completed().observe((LifecycleOwner) activity, completed ->
        {
            if (completed)
            {
                dismiss();
                AWWalletConnectClient.viewModel = null;
            }
        });

        viewModel.findWallet(walletAddress)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onWalletFound);
    }

    private void onWalletFound(Wallet wallet)
    {
        // The find may return the first wallet if the specified wallet not found
        if (!wallet.address.equals(walletAddress) || wallet.watchOnly())
        {
            Toast.makeText(getContext(), "You don't have permission with this wallet.", Toast.LENGTH_SHORT).show();
        } else
        {
            functionBar.setPrimaryButtonWaiting();
            viewModel.sign(activity, walletAddress, sessionRequest, messageTextHex);
        }
    }

}
