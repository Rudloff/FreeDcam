package com.freedcam.apis.i_camera.parameters;

import android.os.Build;

import com.freedcam.utils.AppSettingsManager;
import com.freedcam.ui.I_Activity;
import com.freedcam.utils.DeviceUtils;

/**
 * Created by troop on 21.07.2015.
 */
public class ApiParameter extends AbstractModeParameter
{
    I_Activity i_activity;
    boolean DEBUG = false;

    public ApiParameter(I_Activity i_activity) {
        super(null);
        this.i_activity = i_activity;
    }

    @Override
    public String[] GetValues()
    {
        if (DEBUG || DeviceUtils.IS(DeviceUtils.Devices.LG_G4))
        {
            return new String[]{AppSettingsManager.API_SONY, AppSettingsManager.API_2, AppSettingsManager.API_1};
        }
        else {
            if (Build.VERSION.SDK_INT >= 21) {
                if (AppSettingsManager.APPSETTINGSMANAGER.IsCamera2FullSupported().equals("true"))
                    return new String[]{AppSettingsManager.API_SONY, AppSettingsManager.API_2};
                else
                    return new String[]{AppSettingsManager.API_SONY, AppSettingsManager.API_1};
            } else
                return new String[]{AppSettingsManager.API_SONY, AppSettingsManager.API_1};
        }
    }

    @Override
    public String GetValue() {
        String ret = AppSettingsManager.APPSETTINGSMANAGER.getCamApi();
        if (ret.equals(""))
            ret = AppSettingsManager.API_1;
        return ret;
    }

    @Override
    public void SetValue(String valueToSet, boolean setToCamera) {
        AppSettingsManager.APPSETTINGSMANAGER.setCamApi(valueToSet);
        i_activity.SwitchCameraAPI(valueToSet);
    }

    @Override
    public boolean IsSupported() {
        return true;
    }
}
