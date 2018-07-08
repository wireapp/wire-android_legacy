/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.pages.main.conversationlist.views.listview;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import com.waz.zclient.R;
import timber.log.Timber;

public class ContextWrapperEdgeEffect extends ContextWrapper {

    private ResourcesEdgeEffect resourcesEdgeEffect;
    private int color;
    private Drawable edgeDrawable;
    private Drawable glowDrawable;

    public ContextWrapperEdgeEffect(Context context) {
        this(context, 0);
    }

    public ContextWrapperEdgeEffect(Context context, int color) {
        super(context);
        this.color = color;
        Resources resources = context.getResources();
        resourcesEdgeEffect = new ResourcesEdgeEffect(resources.getAssets(), resources.getDisplayMetrics(), resources.getConfiguration());
    }

    public void setEdgeEffectColor(int color) {
        this.color = color;
        if (edgeDrawable != null) {
            edgeDrawable.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        }
        if (glowDrawable != null) {
            glowDrawable.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        }
    }

    @Override
    public Resources getResources() {
        return resourcesEdgeEffect;
    }

    private class ResourcesEdgeEffect extends Resources {
        private int overscrollEdge = getPlatformDrawableId("overscroll_edge");
        private int overscrollGlow = getPlatformDrawableId("overscroll_glow");

        ResourcesEdgeEffect(AssetManager assets, DisplayMetrics metrics, Configuration config) {
            super(assets, metrics, config);
        }

        private int getPlatformDrawableId(String name) {
            try {
                return (Integer) Class.forName("com.android.internal.R$drawable").getField(name).get(null);
            } catch (ClassNotFoundException e) {
                Timber.e("Internal resource id does not exist: %s", name);
                return 0;
            } catch (NoSuchFieldException e1) {
                Timber.e("Internal resource id does not exist: %s", name);
                return 0;
            } catch (IllegalArgumentException e2) {
                Timber.e("Cannot access internal resource id: %s", name);
                return 0;
            } catch (IllegalAccessException e3) {
                Timber.e("Cannot access internal resource id: %s", name);
            }
            return 0;
        }

        @Override
        public Drawable getDrawable(int resId) throws Resources.NotFoundException {
            Drawable ret;
            if (resId == this.overscrollEdge) {
                edgeDrawable = ContextWrapperEdgeEffect.this.getBaseContext().getResources().getDrawable(R.drawable.overscroll_edge);
                ret = edgeDrawable;
            } else if (resId == this.overscrollGlow) {
                glowDrawable = ContextWrapperEdgeEffect.this.getBaseContext().getResources().getDrawable(R.drawable.overscroll_glow);
                ret = glowDrawable;
            } else {
                return super.getDrawable(resId);
            }

            if (ret != null) {
                ret.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
            }

            return ret;
        }
    }
}
