/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.customization.model.theme;

import static com.android.customization.model.ResourceConstants.ANDROID_PACKAGE;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_COLOR;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_FONT;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_ANDROID;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_SETTINGS;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_SYSUI;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_SHAPE;
import static com.android.customization.model.ResourceConstants.SETTINGS_PACKAGE;
import static com.android.customization.model.ResourceConstants.SYSUI_PACKAGE;
import static com.android.customization.model.ResourceConstants.THEME_SETTING;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.android.customization.model.CustomizationManager.Callback;
import com.android.customization.testutils.Condition;
import com.android.customization.testutils.OverlayManagerMocks;
import com.android.customization.testutils.Wait;
import com.android.wallpaper.module.WallpaperSetter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class ThemeManagerTest {

    @Mock OverlayManagerCompat mMockOm;
    @Mock WallpaperSetter mMockWallpaperSetter;
    private OverlayManagerMocks mMockOmHelper;
    private ThemeManager mThemeManager;
    private FragmentActivity mActivity;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).get();
        mActivity = spy(activity);
        mMockOmHelper = new OverlayManagerMocks();
        mMockOmHelper.setUpMock(mMockOm);
        ThemeBundleProvider provider = mock(ThemeBundleProvider.class);
        mThemeManager = new ThemeManager(provider, activity, mMockWallpaperSetter, mMockOm);
    }

    @After
    public void cleanUp() {
        mMockOmHelper.clearOverlays();
    }

    @Test
    public void testApply_DefaultTheme() {
        mMockOmHelper.addOverlay("test.package.name_color", ANDROID_PACKAGE,
                OVERLAY_CATEGORY_COLOR, true, 0);
        mMockOmHelper.addOverlay("test.package.name_font", ANDROID_PACKAGE,
                OVERLAY_CATEGORY_FONT, true, 0);
        mMockOmHelper.addOverlay("test.package.name_shape", ANDROID_PACKAGE,
                OVERLAY_CATEGORY_SHAPE, true, 0);
        mMockOmHelper.addOverlay("test.package.name_icon", ANDROID_PACKAGE,
                OVERLAY_CATEGORY_ICON_ANDROID, true, 0);
        mMockOmHelper.addOverlay("test.package.name_settings", SETTINGS_PACKAGE,
                OVERLAY_CATEGORY_ICON_SETTINGS, true, 0);
        mMockOmHelper.addOverlay("test.package.name_sysui", SYSUI_PACKAGE,
                OVERLAY_CATEGORY_ICON_SYSUI, true, 0);

        ThemeBundle defaultTheme = new ThemeBundle.Builder().asDefault().build();

        applyThemeAndWaitForCondition(defaultTheme, "Overlays didn't get disabled", () -> {
            verify(mMockOm, times(6)).disableOverlay(anyString(), anyInt());
            return true;
        });

        assertEquals("Secure Setting should be emtpy after applying default theme",
                "",
                Settings.Secure.getString(mActivity.getContentResolver(), THEME_SETTING));
    }

    @Test
    public void testApply_NonDefault() {
        final String bundleColorPackage = "test.package.name_color";
        final String bundleFontPackage = "test.package.name_font";
        final String otherPackage = "other.package.name_font";

        mMockOmHelper.addOverlay(bundleColorPackage, ANDROID_PACKAGE,
                OVERLAY_CATEGORY_COLOR, false, 0);
        mMockOmHelper.addOverlay(bundleFontPackage, ANDROID_PACKAGE,
                OVERLAY_CATEGORY_FONT, false, 0);
        mMockOmHelper.addOverlay(otherPackage, ANDROID_PACKAGE,
                OVERLAY_CATEGORY_FONT, false, 0);

        ThemeBundle theme = new ThemeBundle.Builder()
                .addOverlayPackage(OVERLAY_CATEGORY_COLOR, bundleColorPackage)
                .addOverlayPackage(OVERLAY_CATEGORY_FONT, bundleFontPackage)
                .build();

        applyThemeAndWaitForCondition(theme, "Overlays didn't get enabled", () -> {
            verify(mMockOm, times(2)).setEnabledExclusiveInCategory(anyString(), anyInt());
            Map<String, String> overlays = mMockOm.getEnabledOverlaysForTargets(ANDROID_PACKAGE);
            assertEquals(2, overlays.size());
            assertTrue(bundleColorPackage  + " should be enabled",
                    overlays.values().contains(bundleColorPackage));
            assertTrue(bundleFontPackage  + " should be enabled",
                    overlays.values().contains(bundleFontPackage));
            assertFalse(otherPackage  + " should not be enabled",
                    overlays.values().contains(otherPackage));
            return true;
        });

        assertEquals("Secure Setting was not properly set after applying theme",
                theme.getSerializedPackages(),
                Settings.Secure.getString(mActivity.getContentResolver(), THEME_SETTING));
    }

    private void applyThemeAndWaitForCondition(ThemeBundle theme, String message,
            Condition condition) {
        boolean[] done = {false, false};
        mThemeManager.apply(theme, new Callback() {
            @Override
            public void onSuccess() {
                done[0] = true;
            }

            @Override
            public void onError(@Nullable Throwable throwable) {
                done[0] = true;
                done[1] = true;
            }
        });
        // TODO: refactor these tests so we can get rid of the long wait.
        Wait.atMost(message, () -> done[0] && condition.isTrue(), 1000);
        // done[1] is only set to true in the onError callback.
        assertFalse(done[1]);
    }
}
