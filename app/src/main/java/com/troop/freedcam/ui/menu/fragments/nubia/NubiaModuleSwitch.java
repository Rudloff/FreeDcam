package com.troop.freedcam.ui.menu.fragments.nubia;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.app.Fragment;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.troop.freedcam.R;
import com.troop.freedcam.camera.modules.ModuleHandler;
import com.troop.freedcam.ui.AppSettingsManager;
import com.troop.freedcam.ui.switches.ModuleSwitchHandler;

/**
 * Created by troop on 12.03.2015.
 */
public class NubiaModuleSwitch extends ModuleSwitchHandler
{
    ImageView HDR;
    ImageView Picture;
    ImageView Movie;
    ImageView LongEx;



    ImageView moduleView;

    LinearLayout ModeHouse;
    public NubiaModuleSwitch(View activity, AppSettingsManager appSettingsManager, Fragment fragment)
    {
        super(activity, appSettingsManager, fragment);
    }

    @Override
    protected void init()
    {
        moduleView = (ImageView)activity.findViewById(R.id.imageViewMode);
        moduleView.setOnClickListener(this);
        ModeHouse = (LinearLayout)activity.findViewById(R.id.scrollViewModule);
        ModeHouse.setVisibility(View.GONE);
        Picture = (ImageView)activity.findViewById(R.id.btnMpic);
        Movie = (ImageView)activity.findViewById(R.id.btnMV);
        HDR = (ImageView)activity.findViewById(R.id.btnMHDR);
        LongEx =(ImageView)activity.findViewById(R.id.btnMlongExpo);
    }

    ImageView.OnClickListener PicLabel = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            appSettingsManager.SetCurrentModule("module_picture");
            moduleHandler.SetModule("module_picture");
            Bitmap tmp = BitmapFactory.decodeResource(activity.getResources(), R.drawable.material_ui_mode_pic);
            moduleView.setImageBitmap(tmp);
            ModeHouse.setVisibility(View.GONE);
            iconSwitcher();

        }
    };

    ImageView.OnClickListener HDRLabel = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            appSettingsManager.SetCurrentModule("module_hdr");
            moduleHandler.SetModule("module_hdr");
            Bitmap tmp = BitmapFactory.decodeResource(activity.getResources(), R.drawable.material_ui_mode_hdr);
            moduleView.setImageBitmap(tmp);
            ModeHouse.setVisibility(View.GONE);
            iconSwitcher();

        }
    };

    ImageView.OnClickListener MovieLabel = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            appSettingsManager.SetCurrentModule("module_video");
            moduleHandler.SetModule("module_video");
            Bitmap tmp = BitmapFactory.decodeResource(activity.getResources(), R.drawable.material_ui_mode_vid);
            moduleView.setImageBitmap(tmp);
            ModeHouse.setVisibility(View.GONE);
            iconSwitcher();
        }
    };

    ImageView.OnClickListener LowExLabel = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            appSettingsManager.SetCurrentModule("module_longexposure");
            moduleHandler.SetModule("module_longexposure");
            Bitmap tmp = BitmapFactory.decodeResource(activity.getResources(), R.drawable.material_ui_mode_long);
            moduleView.setImageBitmap(tmp);
            ModeHouse.setVisibility(View.GONE);
            iconSwitcher();

        }
    };

    private void iconSwitcher()
    {
        if (cameraUiWrapper.moduleHandler.GetCurrentModuleName().equals(ModuleHandler.MODULE_VIDEO))
        {
            ImageView VShit = (ImageView)activity.findViewById(R.id.shutter_imageview);
            Bitmap tmp = BitmapFactory.decodeResource(activity.getResources(), R.drawable.material_ui_shutter_vstart);
            VShit.setImageBitmap(tmp);
        }
        else
        {
            ImageView VShit = (ImageView)activity.findViewById(R.id.shutter_imageview);
            Bitmap tmp = BitmapFactory.decodeResource(activity.getResources(), R.drawable.material_ui_shutter);
            VShit.setImageBitmap(tmp);
        }
    }

    private void initButtons()
    {
        if (cameraUiWrapper.moduleHandler.GetCurrentModuleName().equals(ModuleHandler.MODULE_VIDEO))
        {
            Bitmap tmp = BitmapFactory.decodeResource(activity.getResources(), R.drawable.material_ui_mode_vid);
            moduleView.setImageBitmap(tmp);
        }
        else if (cameraUiWrapper.moduleHandler.GetCurrentModuleName().equals(ModuleHandler.MODULE_PICTURE))
        {
            Bitmap tmp = BitmapFactory.decodeResource(activity.getResources(), R.drawable.material_ui_mode_pic);
            moduleView.setImageBitmap(tmp);
        }
        else if (cameraUiWrapper.moduleHandler.GetCurrentModuleName().equals(ModuleHandler.MODULE_HDR))
        {
            Bitmap tmp = BitmapFactory.decodeResource(activity.getResources(), R.drawable.material_ui_mode_hdr);
            moduleView.setImageBitmap(tmp);
        }
        else if (cameraUiWrapper.moduleHandler.GetCurrentModuleName().equals(ModuleHandler.MODULE_LONGEXPO))
        {
            Bitmap tmp = BitmapFactory.decodeResource(activity.getResources(), R.drawable.material_ui_mode_long);
            moduleView.setImageBitmap(tmp);
        }

    }

    @Override
    public void onClick(View v) {
        if(ModeHouse.getVisibility() == View.GONE)
        {
            LinearLayout Flash = (LinearLayout)activity.findViewById(R.id.scrollViewFlash);
            LinearLayout Night = (LinearLayout)activity.findViewById(R.id.scrollViewNight);
            Flash.setVisibility(View.GONE);
            Night.setVisibility(View.GONE);
            Picture.setOnClickListener(PicLabel);
            Movie.setOnClickListener(MovieLabel);
            HDR.setOnClickListener(HDRLabel);
            LongEx.setOnClickListener(LowExLabel);
            if (!cameraUiWrapper.moduleHandler.moduleList.containsKey(ModuleHandler.MODULE_PICTURE))
                Picture.setVisibility(View.GONE);
            if (!cameraUiWrapper.moduleHandler.moduleList.containsKey(ModuleHandler.MODULE_VIDEO))
                Movie.setVisibility(View.GONE);
            if (!cameraUiWrapper.moduleHandler.moduleList.containsKey(ModuleHandler.MODULE_HDR))
                HDR.setVisibility(View.GONE);
            if (!cameraUiWrapper.moduleHandler.moduleList.containsKey(ModuleHandler.MODULE_LONGEXPO))
                LongEx.setVisibility(View.GONE);


            ModeHouse.setVisibility(View.VISIBLE);
        }
        else
        {
            ModeHouse.setVisibility(View.GONE);
        }
    }

    @Override
    public void ParametersLoaded()
    {
        moduleHandler.SetModule(appSettingsManager.GetCurrentModule());
        iconSwitcher();
        initButtons();
        moduleView.setVisibility(View.VISIBLE);

    }
}
