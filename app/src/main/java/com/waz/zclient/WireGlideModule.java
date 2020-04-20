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
package com.waz.zclient;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;
import com.waz.api.MessageContent;
import com.waz.model.GeneralAssetId;
import com.waz.model.Picture;
import com.waz.zclient.core.images.publicasset.PublicAssetFactory;
import com.waz.zclient.shared.assets.usecase.PublicAsset;
import com.waz.zclient.glide.loaders.AssetModelLoader;
import com.waz.zclient.glide.loaders.GoogleMapModelLoader;
import com.waz.zclient.glide.loaders.PictureModelLoader;

import java.io.InputStream;

@GlideModule
public class WireGlideModule extends AppGlideModule {
    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        registry.prepend(GeneralAssetId.class, InputStream.class, new AssetModelLoader.Factory(context));
        registry.prepend(Picture.class, InputStream.class, new PictureModelLoader.Factory(context));
        registry.prepend(MessageContent.Location.class, InputStream.class, new GoogleMapModelLoader.Factory(context));
        registry.prepend(PublicAsset.class, InputStream.class, new PublicAssetFactory().modelLoaderFactory());
    }
}
