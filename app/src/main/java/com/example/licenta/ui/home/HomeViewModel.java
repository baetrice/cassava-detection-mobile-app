package com.example.licenta.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class HomeViewModel extends ViewModel {

    private final MutableLiveData<String> mText;
    private final MutableLiveData<String> appInfo;

    public HomeViewModel() {
        mText = new MutableLiveData<>();
        appInfo = new MutableLiveData<>();

        mText.setValue("Cassava Leaf Disease Detection");
        appInfo.setValue(
                "Key Features:\n" +
                        "- Real-time detection and recognition of cassava leaf diseases using the device's camera.\n" +
                        "- Select an image from the device gallery to detect and recognize leaf diseases.\n\n" +
                        "Detected Diseases:\n" +
                        "- (CBB) Cassava Bacterial Blight\n" +
                        "- (CBSD) Cassava Brown Streak Disease\n" +
                        "- (CGM) Cassava Green Mottle\n" +
                        "- (CMD) Cassava Mosaic Disease\n" +
                        "- Healthy (healthy leaves)"
        );




    }

    public LiveData<String> getText() {
        return mText;
    }

    public LiveData<String> getAppInfo() {
        return appInfo;
    }
}
