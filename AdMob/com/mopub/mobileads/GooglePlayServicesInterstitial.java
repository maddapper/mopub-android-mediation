package com.mopub.mobileads;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;

import java.util.Map;

public class GooglePlayServicesInterstitial extends CustomEventInterstitial {
    /*
     * These keys are intended for MoPub internal use. Do not modify.
     */
    public static final String AD_UNIT_ID_KEY = "adUnitID";
    public static final String LOCATION_KEY = "location";

    private CustomEventInterstitialListener mInterstitialListener;
    private InterstitialAd mGoogleInterstitialAd;

    @Override
    protected void loadInterstitial(
            final Context context,
            final CustomEventInterstitialListener customEventInterstitialListener,
            final Map<String, Object> localExtras,
            final Map<String, String> serverExtras) {
        mInterstitialListener = customEventInterstitialListener;
        final String adUnitId;

        if (extrasAreValid(serverExtras)) {
            adUnitId = serverExtras.get(AD_UNIT_ID_KEY);
        } else {
            mInterstitialListener.onInterstitialFailed(MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
            return;
        }

        mGoogleInterstitialAd = new InterstitialAd(context);
        mGoogleInterstitialAd.setAdListener(new InterstitialAdListener());
        mGoogleInterstitialAd.setAdUnitId(adUnitId);

        final AdRequest adRequest = new AdRequest.Builder()
                .setRequestAgent("MoPub")
                // Consent collected from the MoPub’s consent dialogue should not be used to set up
                // Google's personalization preference. Publishers should work with Google to be GDPR-compliant.
                .addNetworkExtrasBundle(AdMobAdapter.class, getGooglePersonalizationPreference(localExtras))
                .build();

        try {
            mGoogleInterstitialAd.loadAd(adRequest);
        } catch (NoClassDefFoundError e) {
            // This can be thrown by Play Services on Honeycomb.
            mInterstitialListener.onInterstitialFailed(MoPubErrorCode.NETWORK_NO_FILL);
        }
    }

    @Override
    protected void showInterstitial() {
        if (mGoogleInterstitialAd.isLoaded()) {
            mGoogleInterstitialAd.show();
        } else {
            Log.d("MoPub", "Tried to show a Google Play Services interstitial ad before it finished loading. Please try again.");
        }
    }

    @Override
    protected void onInvalidate() {
        if (mGoogleInterstitialAd != null) {
            mGoogleInterstitialAd.setAdListener(null);
        }
    }

    private Bundle getGooglePersonalizationPreference(Map<String, Object> localExtras) {
        Bundle extras = new Bundle();
        if (localExtras.get("npa") != null) {
            String personalizationPref = localExtras.get("npa").toString();
            if (!TextUtils.isEmpty(personalizationPref)) {
                extras.putString("npa", personalizationPref);
            }
        }
        return extras;
    }

    private boolean extrasAreValid(Map<String, String> serverExtras) {
        return serverExtras.containsKey(AD_UNIT_ID_KEY);
    }

    private class InterstitialAdListener extends AdListener {
        /*
         * Google Play Services AdListener implementation
         */
        @Override
        public void onAdClosed() {
            Log.d("MoPub", "Google Play Services interstitial ad dismissed.");
            if (mInterstitialListener != null) {
                mInterstitialListener.onInterstitialDismissed();
            }
        }

        @Override
        public void onAdFailedToLoad(int errorCode) {
            Log.d("MoPub", "Google Play Services interstitial ad failed to load.");
            if (mInterstitialListener != null) {
                mInterstitialListener.onInterstitialFailed(getMoPubErrorCode(errorCode));
            }
        }

        @Override
        public void onAdLeftApplication() {
            Log.d("MoPub", "Google Play Services interstitial ad clicked.");
            if (mInterstitialListener != null) {
                mInterstitialListener.onInterstitialClicked();
            }
        }

        @Override
        public void onAdLoaded() {
            Log.d("MoPub", "Google Play Services interstitial ad loaded successfully.");
            if (mInterstitialListener != null) {
                mInterstitialListener.onInterstitialLoaded();
            }
        }

        @Override
        public void onAdOpened() {
            Log.d("MoPub", "Showing Google Play Services interstitial ad.");
            if (mInterstitialListener != null) {
                mInterstitialListener.onInterstitialShown();
            }
        }

        /**
         * Converts a given Google Mobile Ads SDK error code into {@link MoPubErrorCode}.
         *
         * @param error Google Mobile Ads SDK error code.
         * @return an equivalent MoPub SDK error code for the given Google Mobile Ads SDK error
         * code.
         */
        private MoPubErrorCode getMoPubErrorCode(int error) {
            MoPubErrorCode errorCode;
            switch (error) {
                case AdRequest.ERROR_CODE_INTERNAL_ERROR:
                    errorCode = MoPubErrorCode.INTERNAL_ERROR;
                    break;
                case AdRequest.ERROR_CODE_INVALID_REQUEST:
                    errorCode = MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR;
                    break;
                case AdRequest.ERROR_CODE_NETWORK_ERROR:
                    errorCode = MoPubErrorCode.NO_CONNECTION;
                    break;
                case AdRequest.ERROR_CODE_NO_FILL:
                    errorCode = MoPubErrorCode.NO_FILL;
                    break;
                default:
                    errorCode = MoPubErrorCode.UNSPECIFIED;
            }
            return errorCode;
        }
    }

    @Deprecated
        // for testing
    InterstitialAd getGoogleInterstitialAd() {
        return mGoogleInterstitialAd;
    }
}
