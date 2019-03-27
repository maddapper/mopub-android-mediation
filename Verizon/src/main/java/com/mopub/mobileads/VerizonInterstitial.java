package com.mopub.mobileads;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.mopub.common.MoPub;
import com.mopub.common.logging.MoPubLog;
import com.verizon.ads.CreativeInfo;
import com.verizon.ads.ErrorInfo;
import com.verizon.ads.RequestMetadata;
import com.verizon.ads.VASAds;
import com.verizon.ads.edition.StandardEdition;
import com.verizon.ads.interstitialplacement.InterstitialAd;
import com.verizon.ads.interstitialplacement.InterstitialAdFactory;

import java.util.Map;

import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CLICKED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CUSTOM;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.DID_DISAPPEAR;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_FAILED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_SUCCESS;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_ATTEMPTED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_FAILED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_SUCCESS;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.WILL_LEAVE_APPLICATION;

public class VerizonInterstitial extends CustomEventInterstitial {

    private static final String ADAPTER_NAME = VerizonInterstitial.class.getSimpleName();

    private static final String PLACEMENT_ID_KEY = "placementId";
    private static final String SITE_ID_KEY = "siteId";

    private Context context;
    private CustomEventInterstitialListener interstitialListener;
    private InterstitialAd verizonInterstitialAd;

    @NonNull
    private VerizonAdapterConfiguration verizonAdapterConfiguration;

    public VerizonInterstitial() {
        verizonAdapterConfiguration = new VerizonAdapterConfiguration();
    }

    @Override
    protected void loadInterstitial(final Context context,
                                    final CustomEventInterstitialListener customEventInterstitialListener, final Map<String, Object> localExtras,
                                    final Map<String, String> serverExtras) {

        interstitialListener = customEventInterstitialListener;
        this.context = context;

        if (serverExtras == null || serverExtras.isEmpty()) {
            MoPubLog.log(CUSTOM, ADAPTER_NAME, "Ad request to Verizon failed because " +
                    "serverExtras is null or empty");
            MoPubLog.log(LOAD_FAILED, ADAPTER_NAME, MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR.getIntCode(),
                    MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);

            if (interstitialListener != null) {
                interstitialListener.onInterstitialFailed(MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
            }

            return;
        }

        if (!VASAds.isInitialized()) {
            final String siteId = serverExtras.get(SITE_ID_KEY);

            if (TextUtils.isEmpty(siteId)) {
                MoPubLog.log(CUSTOM, ADAPTER_NAME, "Ad request to Verizon failed because " +
                        "siteId is empty");
                MoPubLog.log(LOAD_FAILED, ADAPTER_NAME, MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR.getIntCode(),
                        MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);

                if (interstitialListener != null) {
                    interstitialListener.onInterstitialFailed(MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
                }

                return;
            }

            if (!(context instanceof Activity)) {
                MoPubLog.log(CUSTOM, ADAPTER_NAME, "Ad request to Verizon failed because " +
                        "context is not an Activity");
                MoPubLog.log(LOAD_FAILED, ADAPTER_NAME, MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR.getIntCode(),
                        MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);

                if (interstitialListener != null) {
                    interstitialListener.onInterstitialFailed(MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
                }

                return;
            }

            final boolean success = StandardEdition.initializeWithActivity((Activity) context, siteId);

            if (!success) {
                MoPubLog.log(CUSTOM, ADAPTER_NAME, "Failed to initialize the Verizon SDK");
                MoPubLog.log(LOAD_FAILED, ADAPTER_NAME, MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR.getIntCode(),
                        MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);

                if (interstitialListener != null) {
                    interstitialListener.onInterstitialFailed(MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
                }

                return;
            }
        }

        final String placementId = serverExtras.get(PLACEMENT_ID_KEY);

        if (TextUtils.isEmpty(placementId)) {
            MoPubLog.log(CUSTOM, ADAPTER_NAME, "Ad request to Verizon failed because placement " +
                    "ID is empty");
            MoPubLog.log(LOAD_FAILED, ADAPTER_NAME, MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR.getIntCode(),
                    MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);

            if (interstitialListener != null) {
                interstitialListener.onInterstitialFailed(MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
            }

            return;
        }

        VASAds.setLocationEnabled(MoPub.getLocationAwareness() != MoPub.LocationAwareness.DISABLED);

        final InterstitialAdFactory interstitialAdFactory = new InterstitialAdFactory(context, placementId,
                new VerizonInterstitialFactoryListener());

        final RequestMetadata requestMetadata = new RequestMetadata.Builder().setMediator(VerizonAdapterConfiguration.MEDIATOR_ID).build();
        interstitialAdFactory.setRequestMetaData(requestMetadata);
        interstitialAdFactory.load(new VerizonInterstitialListener());

        verizonAdapterConfiguration.setCachedInitializationParameters(context, serverExtras);
    }

    @Override
    protected void showInterstitial() {

        MoPubLog.log(SHOW_ATTEMPTED, ADAPTER_NAME, "Showing Verizon interstitial");

        VerizonUtils.postOnUiThread(new Runnable() {

            @Override
            public void run() {
                if (verizonInterstitialAd != null) {
                    verizonInterstitialAd.show(context);
                    return;
                }

                if (interstitialListener != null) {
                    interstitialListener.onInterstitialFailed(MoPubErrorCode.INTERNAL_ERROR);
                }
                MoPubLog.log(SHOW_FAILED, ADAPTER_NAME, MoPubErrorCode.UNSPECIFIED, "Failed to " +
                        "show Verizon interstitial. It is not ready");
            }
        });
    }

    @Override
    protected void onInvalidate() {

        VerizonUtils.postOnUiThread(new Runnable() {

            @Override
            public void run() {
                interstitialListener = null;

                // Destroy any hanging references
                if (verizonInterstitialAd != null) {
                    verizonInterstitialAd.destroy();
                    verizonInterstitialAd = null;
                }
            }
        });
    }

    class VerizonInterstitialFactoryListener implements InterstitialAdFactory.InterstitialAdFactoryListener {
        final CustomEventInterstitialListener listener = interstitialListener;

        @Override
        public void onLoaded(final InterstitialAdFactory interstitialAdFactory, final InterstitialAd interstitialAd) {

            verizonInterstitialAd = interstitialAd;

            MoPubLog.log(LOAD_SUCCESS, ADAPTER_NAME);

            final CreativeInfo creativeInfo = verizonInterstitialAd == null ? null : verizonInterstitialAd.getCreativeInfo();
            MoPubLog.log(CUSTOM, ADAPTER_NAME, "Verizon creative info: " + creativeInfo);

            VerizonUtils.postOnUiThread(new Runnable() {

                @Override
                public void run() {
                    if (listener != null) {
                        listener.onInterstitialLoaded();
                    }
                }
            });
        }

        @Override
        public void onCacheLoaded(final InterstitialAdFactory interstitialAdFactory, final int i, final int i1) {
        }

        @Override
        public void onCacheUpdated(final InterstitialAdFactory interstitialAdFactory, final int i) {
        }

        @Override
        public void onError(final InterstitialAdFactory interstitialAdFactory, final ErrorInfo errorInfo) {

            MoPubLog.log(CUSTOM, ADAPTER_NAME, "Failed to load Verizon interstitial due to " +
                    "error: " + errorInfo);
            VerizonUtils.postOnUiThread(new Runnable() {

                @Override
                public void run() {
                    final MoPubErrorCode errorCode = VerizonUtils.convertErrorCodeToMoPub(errorInfo.getErrorCode());

                    if (listener != null) {
                        listener.onInterstitialFailed(errorCode);
                    }
                    MoPubLog.log(LOAD_FAILED, ADAPTER_NAME, errorCode.getIntCode(), errorCode);
                }
            });
        }
    }

    private class VerizonInterstitialListener implements InterstitialAd.InterstitialAdListener {
        final CustomEventInterstitialListener listener = interstitialListener;

        @Override
        public void onError(final InterstitialAd interstitialAd, final ErrorInfo errorInfo) {

            MoPubLog.log(CUSTOM, ADAPTER_NAME, "Failed to show Verizon interstitial due to " +
                    "error: " + errorInfo);
            VerizonUtils.postOnUiThread(new Runnable() {

                @Override
                public void run() {
                    final MoPubErrorCode errorCode = VerizonUtils.convertErrorCodeToMoPub(errorInfo.getErrorCode());

                    if (listener != null) {
                        listener.onInterstitialFailed(errorCode);
                    }
                    MoPubLog.log(SHOW_FAILED, ADAPTER_NAME, errorCode.getIntCode(), errorCode);
                }
            });
        }

        @Override
        public void onShown(final InterstitialAd interstitialAd) {

            MoPubLog.log(SHOW_SUCCESS, ADAPTER_NAME);
            VerizonUtils.postOnUiThread(new Runnable() {

                @Override
                public void run() {
                    if (listener != null) {
                        listener.onInterstitialShown();
                    }
                }
            });
        }

        @Override
        public void onClosed(final InterstitialAd interstitialAd) {

            MoPubLog.log(DID_DISAPPEAR, ADAPTER_NAME);
            VerizonUtils.postOnUiThread(new Runnable() {

                @Override
                public void run() {
                    if (listener != null) {
                        listener.onInterstitialDismissed();
                    }
                }
            });
        }

        @Override
        public void onClicked(final InterstitialAd interstitialAd) {

            MoPubLog.log(CLICKED, ADAPTER_NAME);
            VerizonUtils.postOnUiThread(new Runnable() {

                @Override
                public void run() {
                    if (listener != null) {
                        listener.onInterstitialClicked();
                    }
                }
            });
        }

        @Override
        public void onAdLeftApplication(final InterstitialAd interstitialAd) {
            // Only logging this event. No need to call interstitialListener.onLeaveApplication()
            // because it's an alias for interstitialListener.onInterstitialClicked()
            MoPubLog.log(WILL_LEAVE_APPLICATION, ADAPTER_NAME);
        }

        @Override
        public void onEvent(final InterstitialAd interstitialAd, final String s, final String s1, final Map<String, Object> map) {
        }
    }
}
