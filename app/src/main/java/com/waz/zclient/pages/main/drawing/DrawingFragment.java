/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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
package com.waz.zclient.pages.main.drawing;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.ExifInterface;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import com.waz.api.BitmapCallback;
import com.waz.api.ImageAsset;
import com.waz.api.ImageAssetFactory;
import com.waz.api.LoadHandle;
import com.waz.api.MemoryImageCache;
import com.waz.utils.wrappers.URI;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.drawing.DrawingController;
import com.waz.zclient.controllers.drawing.IDrawingController;
import com.waz.zclient.controllers.globallayout.KeyboardVisibilityObserver;
import com.waz.zclient.controllers.permission.RequestPermissionsObserver;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.conversation.AssetIntentsManager;
import com.waz.zclient.ui.colorpicker.ColorPickerLayout;
import com.waz.zclient.ui.colorpicker.EmojiBottomSheetDialog;
import com.waz.zclient.ui.colorpicker.EmojiSize;
import com.waz.zclient.ui.sketch.DrawingCanvasCallback;
import com.waz.zclient.ui.sketch.DrawingCanvasView;
import com.waz.zclient.ui.text.TypefaceTextView;
import com.waz.zclient.ui.utils.ColorUtils;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.ui.utils.MathUtils;
import com.waz.zclient.ui.views.CursorIconButton;
import com.waz.zclient.ui.views.SketchEditText;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.PermissionUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.utils.debug.ShakeEventListener;

import java.util.Locale;

public class DrawingFragment extends BaseFragment<DrawingFragment.Container> implements OnBackPressedListener,
                                                                                        ColorPickerLayout.OnColorSelectedListener,
                                                                                        DrawingCanvasCallback,
                                                                                        ViewTreeObserver.OnScrollChangedListener,
                                                                                        AccentColorObserver,
                                                                                        AssetIntentsManager.Callback,
                                                                                        RequestPermissionsObserver,
                                                                                        ColorPickerLayout.OnWidthChangedListener,
                                                                                        KeyboardVisibilityObserver {

    public static final String TAG = DrawingFragment.class.getName();
    private static final String SAVED_INSTANCE_BITMAP = "SAVED_INSTANCE_BITMAP";
    private static final String ARGUMENT_BACKGROUND_IMAGE = "ARGUMENT_BACKGROUND_IMAGE";
    private static final String ARGUMENT_DRAWING_DESTINATION = "ARGUMENT_DRAWING_DESTINATION";
    private static final String ARGUMENT_DRAWING_METHOD = "ARGUMENT_DRAWING_METHOD";

    private static final String[] SKETCH_FROM_GALLERY_PERMISSION = new String[] {Manifest.permission.READ_EXTERNAL_STORAGE};
    private static final int OPEN_SKETCH_FROM_GALLERY = 8864;

    private static final float TEXT_ALPHA_INVISIBLE = 0F;
    private static final float TEXT_ALPHA_MOVE = 0.2F;
    private static final float TEXT_ALPHA_VISIBLE = 1F;
    private static final int SEND_BUTTON_DISABLED_ALPHA = 102;
    private static final int DEFAULT_TEXT_COLOR = Color.WHITE;
    private static final int EDIT_BOX_POS_MARGIN = 10;

    private ShakeEventListener shakeEventListener;
    private SensorManager sensorManager;
    private AssetIntentsManager assetIntentsManager;
    private DrawingCanvasView drawingCanvasView;
    private ColorPickerLayout colorLayout;
    private HorizontalScrollView colorPickerScrollContainer;
    private Toolbar toolbar;

    // TODO uncomment once AN-4649 is fixed
    //private ColorPickerScrollView colorPickerScrollBar;

    private TypefaceTextView drawingViewTip;
    private View drawingTipBackground;

    private CursorIconButton sendDrawingButton;
    private SketchEditText sketchEditTextView;
    private boolean shouldOpenEditText = false;
    private int currentBackgroundColor;
    private TextView actionButtonText;
    private TextView actionButtonEmoji;
    private TextView actionButtonSketch;
    private View galleryButton;
    private int defaultTextColor;

    private ImageAsset backgroundImage;
    private LoadHandle bitmapLoadHandle;

    private DrawingController.DrawingDestination drawingDestination;
    private DrawingController.DrawingMethod drawingMethod;
    private boolean includeBackgroundImage;
    private EmojiSize currentEmojiSize = EmojiSize.SMALL;

    private View.OnTouchListener drawingCanvasViewOnTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    hideTip();
                    break;
            }
            return false;
        }
    };
    private Toolbar.OnMenuItemClickListener toolbarOnMenuItemClickListener =  new Toolbar.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.close:
                    if (getControllerFactory() == null || getControllerFactory().isTornDown()) {
                        return false;
                    }
                    getControllerFactory().getDrawingController().hideDrawing(drawingDestination, false);
                    return true;
            }
            return false;
        }
    };
    private View.OnClickListener toolbarNavigationClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (drawingCanvasView != null) {
                KeyboardUtils.hideKeyboard(getActivity());
                drawingCanvasView.undo();
            }
        }
    };
    private View.OnTouchListener sketchEditTextOnTouchListener = new View.OnTouchListener() {
        private float initialX;
        private float initialY;
        private FrameLayout.LayoutParams params;
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (drawingCanvasView.getCurrentMode() == DrawingCanvasView.Mode.TEXT) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = event.getX();
                        initialY = event.getY();
                        params = (FrameLayout.LayoutParams) sketchEditTextView.getLayoutParams();
                        sketchEditTextView.setAlpha(TEXT_ALPHA_MOVE);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        params.leftMargin += (int) (event.getX() - initialX);
                        params.topMargin += (int) (event.getY() - initialY);
                        clampSketchEditBoxPosition(params);
                        sketchEditTextView.setLayoutParams(params);
                        break;
                    case MotionEvent.ACTION_UP:
                        closeKeyboard();
                        break;
                }
            }
            return drawingCanvasView.onTouchEvent(event);
        }
    };
    private SketchEditText.SketchEditTextListener sketchEditTextListener = new SketchEditText.SketchEditTextListener() {
        @Override
        public void editTextChanged() {
            sketchEditTextView.setBackground(ColorUtils.getRoundedTextBoxBackground(getContext(), currentBackgroundColor, sketchEditTextView.getMeasuredHeight()));
        }
    };

    public static DrawingFragment newInstance(ImageAsset backgroundAsset, DrawingController.DrawingDestination drawingDestination) {
        return DrawingFragment.newInstance(backgroundAsset, drawingDestination, IDrawingController.DrawingMethod.DRAW);
    }

    public static DrawingFragment newInstance(ImageAsset backgroundAsset, DrawingController.DrawingDestination drawingDestination, DrawingController.DrawingMethod method) {
        DrawingFragment fragment = new DrawingFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(ARGUMENT_BACKGROUND_IMAGE, backgroundAsset);
        bundle.putString(ARGUMENT_DRAWING_DESTINATION, drawingDestination.toString());
        bundle.putString(ARGUMENT_DRAWING_METHOD, method.toString());
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        backgroundImage = args.getParcelable(ARGUMENT_BACKGROUND_IMAGE);
        drawingDestination = DrawingController.DrawingDestination.valueOf(args.getString(ARGUMENT_DRAWING_DESTINATION));
        drawingMethod = DrawingController.DrawingMethod.valueOf(args.getString(ARGUMENT_DRAWING_METHOD));
        sensorManager = (SensorManager) getActivity().getSystemService(Activity.SENSOR_SERVICE);
        defaultTextColor = ContextCompat.getColor(getContext(), R.color.text__primary_light);
        assetIntentsManager = new AssetIntentsManager(getActivity(), this, savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_drawing, container, false);
        drawingCanvasView = ViewUtils.getView(rootView, R.id.dcv__canvas);
        drawingCanvasView.setDrawingCanvasCallback(this);
        drawingCanvasView.setDrawingColor(getControllerFactory().getAccentColorController().getColor());
        drawingCanvasView.setOnTouchListener(drawingCanvasViewOnTouchListener);

        colorPickerScrollContainer = ViewUtils.getView(rootView, R.id.hsv_color_picker_scroll_view);

        colorLayout = ViewUtils.getView(rootView, R.id.cpdl__color_layout);
        colorLayout.setOnColorSelectedListener(this);
        int[] colors = getResources().getIntArray(R.array.draw_color);
        colorLayout.setAccentColors(colors, getControllerFactory().getAccentColorController().getColor());
        colorLayout.getViewTreeObserver().addOnScrollChangedListener(this);

        // TODO uncomment once AN-4649 is fixed
//        colorPickerScrollBar = ViewUtils.getView(rootView, R.id.cpsb__color_picker_scrollbar);
//        colorPickerScrollBar.setScrollBarColor(getControllerFactory().getAccentColorController().getColor());

        TypefaceTextView conversationTitle = ViewUtils.getView(rootView, R.id.tv__drawing_toolbar__title);
        conversationTitle.setText(getStoreFactory().conversationStore().getCurrentConversation().getName().toUpperCase(Locale.getDefault()));
        toolbar = ViewUtils.getView(rootView, R.id.t_drawing_toolbar);
        toolbar.inflateMenu(R.menu.toolbar_sketch);
        toolbar.setOnMenuItemClickListener(toolbarOnMenuItemClickListener);
        toolbar.setNavigationOnClickListener(toolbarNavigationClickListener);
        toolbar.setNavigationIcon(R.drawable.toolbar_action_undo_disabled);

        actionButtonText = ViewUtils.getView(rootView, R.id.gtv__drawing_button__text);
        actionButtonText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTextClick();
            }
        });
        actionButtonEmoji = ViewUtils.getView(rootView, R.id.gtv__drawing_button__emoji);
        actionButtonEmoji.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onEmojiClick();
            }
        });
        actionButtonSketch = ViewUtils.getView(rootView, R.id.gtv__drawing_button__sketch);
        actionButtonSketch.setTextColor(getControllerFactory().getAccentColorController().getColor());
        actionButtonSketch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSketchClick();
            }
        });

        galleryButton = ViewUtils.getView(rootView, R.id.gtv__drawing__gallery_button);
        galleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (PermissionUtils.hasSelfPermissions(getContext(), SKETCH_FROM_GALLERY_PERMISSION)) {
                    sketchEditTextView.destroyDrawingCache();
                    assetIntentsManager.openGalleryForSketch();
                } else {
                    ActivityCompat.requestPermissions(getActivity(),
                                                      SKETCH_FROM_GALLERY_PERMISSION,
                                                      OPEN_SKETCH_FROM_GALLERY);
                }
            }
        });

        drawingTipBackground = ViewUtils.getView(rootView, R.id.v__tip_background);
        drawingViewTip = ViewUtils.getView(rootView, R.id.ttv__drawing__view__tip);
        drawingTipBackground.setVisibility(View.INVISIBLE);

        sendDrawingButton = ViewUtils.getView(rootView, R.id.tv__send_button);
        sendDrawingButton.setOnClickListener(sketchButtonsOnClickListener);
        sendDrawingButton.setClickable(false);

        sketchEditTextView = ViewUtils.getView(rootView, R.id.et__sketch_text);
        sketchEditTextView.setAlpha(TEXT_ALPHA_INVISIBLE);
        sketchEditTextView.setVisibility(View.INVISIBLE);
        sketchEditTextView.setCustomHint(getString(R.string.drawing__text_hint));
        currentBackgroundColor = getControllerFactory().getAccentColorController().getColor();
        sketchEditTextView.setBackground(ColorUtils.getTransparentDrawable());
        sketchEditTextView.setHintFontId(R.string.wire__typeface__medium);
        sketchEditTextView.setTextFontId(R.string.wire__typeface__regular);
        sketchEditTextView.setSketchScale(1.0f);
        sketchEditTextView.setOnTouchListener(sketchEditTextOnTouchListener);

        if (savedInstanceState != null) {
            Bitmap savedBitmap = savedInstanceState.getParcelable(SAVED_INSTANCE_BITMAP);
            if (savedBitmap != null) {
                // Use saved background image if exists
                drawingCanvasView.setBackgroundBitmap(savedBitmap);
            } else {
                setBackgroundBitmap(true);
            }
        } else {
            setBackgroundBitmap(true);
        }

        return rootView;
    }

    private void hideTip() {
        if (drawingViewTip.getVisibility() == View.GONE) {
            return;
        }
        drawingViewTip.setVisibility(View.GONE);
        drawingTipBackground.setVisibility(View.GONE);
        drawingCanvasView.setOnTouchListener(null);
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(SAVED_INSTANCE_BITMAP, getBitmapDrawing());
        assetIntentsManager.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    public void setBackgroundBitmap(boolean showHint) {
        if (getActivity() == null || backgroundImage == null) {
            return;
        }

        if (showHint) {
            drawingViewTip.setText(getResources().getString(R.string.drawing__tip__picture__message));
            drawingTipBackground.setVisibility(View.VISIBLE);
        } else {
            hideTip();
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            drawingViewTip.setTextColor(getResources().getColor(R.color.drawing__tip__font__color_image));
        } else {
            drawingViewTip.setTextColor(getResources().getColor(R.color.drawing__tip__font__color_image, getContext().getTheme()));
        }
        cancelLoadHandle();
        bitmapLoadHandle = backgroundImage.getSingleBitmap(ViewUtils.getOrientationDependentDisplayWidth(getActivity()), new BitmapCallback() {
            @Override
            public void onBitmapLoaded(Bitmap bitmap) {
                if (getActivity() == null || drawingCanvasView == null) {
                    return;
                }
                includeBackgroundImage = true;
                drawingCanvasView.setBackgroundBitmap(bitmap);
                cancelLoadHandle();

                if (drawingMethod == IDrawingController.DrawingMethod.EMOJI) {
                    onEmojiClick();
                } else if (drawingMethod == IDrawingController.DrawingMethod.TEXT) {
                    onTextClick();
                }
            }

            @Override
            public void onBitmapLoadingFailed() {
                cancelLoadHandle();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        colorLayout.setOnWidthChangedListener(this);
        shakeEventListener = new ShakeEventListener();
        shakeEventListener.setOnShakeListener(new ShakeEventListener.OnShakeListener() {
            @Override
            public void onShake() {
                if (includeBackgroundImage) {
                    drawingCanvasView.removeBackgroundBitmap();
                    includeBackgroundImage = false;
                } else {
                    drawingCanvasView.drawBackgroundBitmap();
                    includeBackgroundImage = true;
                }
            }
        });
        sensorManager.registerListener(shakeEventListener,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);
        getControllerFactory().getGlobalLayoutController().addKeyboardVisibilityObserver(this);
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        getControllerFactory().getRequestPermissionsController().addObserver(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (LayoutSpec.isTablet(getActivity())) {
            ViewUtils.lockScreenOrientation(Configuration.ORIENTATION_PORTRAIT, getActivity());
        }
    }

    @Override
    public void onStop() {
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        getControllerFactory().getGlobalLayoutController().removeKeyboardVisibilityObserver(this);
        getControllerFactory().getRequestPermissionsController().removeObserver(this);
        sensorManager.unregisterListener(shakeEventListener);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (drawingCanvasView != null) {
            drawingCanvasView.onDestroy();
            drawingCanvasView = null;
        }
        colorLayout = null;
        sendDrawingButton = null;
        cancelLoadHandle();
        super.onDestroyView();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        assetIntentsManager.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onDataReceived(AssetIntentsManager.IntentType type, URI uri) {
        switch (type) {
            case SKETCH_FROM_GALLERY:
                drawingMethod = IDrawingController.DrawingMethod.DRAW;
                sketchEditTextView.setText("");
                sketchEditTextView.setVisibility(View.GONE);
                drawingCanvasView.reset();
                drawingCanvasView.removeBackgroundBitmap();
                backgroundImage = ImageAssetFactory.getImageAsset(uri);
                setBackgroundBitmap(false);
                onSketchClick();
                break;
        }
    }

    @Override
    public void onCanceled(AssetIntentsManager.IntentType type) {

    }

    @Override
    public void onFailed(AssetIntentsManager.IntentType type) {

    }

    @Override
    public void openIntent(Intent intent, AssetIntentsManager.IntentType intentType) {
        startActivityForResult(intent, intentType.requestCode);
        getActivity().overridePendingTransition(R.anim.camera_in, R.anim.camera_out);
    }

    @Override
    public void onPermissionFailed(AssetIntentsManager.IntentType type) {

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (assetIntentsManager.onRequestPermissionsResult(requestCode, grantResults)) {
            return;
        }


        switch (requestCode) {
            case OPEN_SKETCH_FROM_GALLERY:
                if (PermissionUtils.verifyPermissions(grantResults)) {
                    assetIntentsManager.openGalleryForSketch();
                }
        }
    }

    public void cancelLoadHandle() {
        if (bitmapLoadHandle != null) {
            bitmapLoadHandle.cancel();
            bitmapLoadHandle = null;
        }
    }

    private Bitmap getBitmapDrawing() {
        return drawingCanvasView.getBitmap();
    }

    @Override
    public boolean onBackPressed() {
        if (isShowingKeyboard()) {
            closeKeyboard();
            return true;
        }
        if (drawingCanvasView != null && drawingCanvasView.undo()) {
            return true;
        }
        getControllerFactory().getDrawingController().hideDrawing(drawingDestination, false);
        return true;
    }

    @Override
    public void onScrollWidthChanged(int width) {
        // TODO uncomment once AN-4649 is fixed
        //colorPickerScrollBar.setScrollBarSize(width);
    }

    private View.OnClickListener sketchButtonsOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (drawingCanvasView == null) {
                return;
            }
            switch (v.getId()) {
                case R.id.tv__send_button:
                    if (!drawingCanvasView.isEmpty()) {
                        getStoreFactory().conversationStore().sendMessage(getFinalSketchImage());
                        getControllerFactory().getDrawingController().hideDrawing(drawingDestination, true);
                    }
                    break;
                default:
                    //nothing
                    break;
            }
        }
    };

    private ImageAsset getFinalSketchImage() {
        Bitmap finalBitmap = getBitmapDrawing();
        try {
            Rect bitmapTrim = drawingCanvasView.getImageTrimValues();
            MemoryImageCache.reserveImageMemory(bitmapTrim.width(), bitmapTrim.height());
            finalBitmap = Bitmap.createBitmap(finalBitmap, bitmapTrim.left, bitmapTrim.top, bitmapTrim.width(), bitmapTrim.height());
        } catch (Throwable t) {
            // ignore
        }
        return ImageAssetFactory.getImageAsset(finalBitmap, ExifInterface.ORIENTATION_NORMAL);
    }

    @Override
    public void onScrollChanged() {
        // TODO uncomment once AN-4649 is fixed
        //if (colorPickerScrollBar == null || colorLayout == null) {
        //    return;
        //}
        //colorPickerScrollBar.setLeftX(colorPickerScrollContainer.getScrollX());
    }

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        // TODO uncomment once AN-4649 is fixed
        //colorPickerScrollBar.setBackgroundColor(color);
        if (drawingCanvasView.isEmpty()) {
            int accentColorWithAlpha = ColorUtils.injectAlpha(SEND_BUTTON_DISABLED_ALPHA, getControllerFactory().getAccentColorController().getColor());
            sendDrawingButton.setSolidBackgroundColor(accentColorWithAlpha);
        } else {
            sendDrawingButton.setSolidBackgroundColor(getControllerFactory().getAccentColorController().getColor());
        }
    }

    @Override
    public void onColorSelected(int color, int strokeSize) {
        if (drawingCanvasView == null) {
            return;
        }
        drawingCanvasView.setDrawingColor(color);
        drawingCanvasView.setStrokeSize(strokeSize);
        currentBackgroundColor = color;
        sketchEditTextView.setBackground(ColorUtils.getRoundedTextBoxBackground(getContext(), color, sketchEditTextView.getHeight()));
        if (MathUtils.floatEqual(sketchEditTextView.getAlpha(), TEXT_ALPHA_INVISIBLE)) {
            drawSketchEditText();
        }
    }

    public void onEmojiClick() {
        final EmojiBottomSheetDialog dialog = new EmojiBottomSheetDialog(getContext(),
                                                                         currentEmojiSize,
                                                                         new EmojiBottomSheetDialog.EmojiDialogListener() {
                                                                             @Override
                                                                             public void onEmojiSelected(String emoji, EmojiSize emojiSize) {
                                                                                 actionButtonEmoji.setTextColor(getControllerFactory().getAccentColorController().getColor());
                                                                                 actionButtonSketch.setTextColor(defaultTextColor);
                                                                                 actionButtonText.setTextColor(defaultTextColor);
                                                                                 drawingCanvasView.setEmoji(emoji, emojiSize.getEmojiSize(getContext()));
                                                                                 currentEmojiSize = emojiSize;
                                                                                 getControllerFactory().getUserPreferencesController().addRecentEmoji(emoji);
                                                                             }
                                                                         },
                                                                         getControllerFactory().getUserPreferencesController().getRecentEmojis(),
                                                                         getControllerFactory().getUserPreferencesController().getUnsupportedEmojis());
        dialog.show();
    }

    private void closeKeyboard() {
        drawSketchEditText();
        KeyboardUtils.hideKeyboard(getActivity());
    }

    private void showKeyboard() {
        drawingCanvasView.hideText();
        sketchEditTextView.setCursorVisible(true);
        sketchEditTextView.requestFocus();
        KeyboardUtils.showKeyboard(getActivity());
    }

    private boolean isShowingKeyboard() {
        return sketchEditTextView.getVisibility() == View.VISIBLE &&
            Float.compare(sketchEditTextView.getAlpha(), TEXT_ALPHA_VISIBLE) == 0 &&
            KeyboardUtils.isKeyboardVisible(getContext());
    }

    private void onSketchClick() {
        drawingCanvasView.setCurrentMode(DrawingCanvasView.Mode.SKETCH);
        actionButtonText.setTextColor(defaultTextColor);
        actionButtonEmoji.setTextColor(defaultTextColor);
        actionButtonSketch.setTextColor(getControllerFactory().getAccentColorController().getColor());
    }

    private void onTextClick() {
        actionButtonText.setTextColor(getControllerFactory().getAccentColorController().getColor());
        actionButtonEmoji.setTextColor(defaultTextColor);
        actionButtonSketch.setTextColor(defaultTextColor);
        drawingCanvasView.setCurrentMode(DrawingCanvasView.Mode.TEXT);
        if (isShowingKeyboard()) {
            closeKeyboard();
        } else {
            if (sketchEditTextView.getVisibility() != View.VISIBLE) {
                shouldOpenEditText = true;
                sketchEditTextView.setAlpha(TEXT_ALPHA_INVISIBLE);
                sketchEditTextView.setBackground(ColorUtils.getTransparentDrawable());
            }
            sketchEditTextView.setVisibility(View.VISIBLE);
            showKeyboard();
            hideTip();
        }
    }

    @Override
    public void drawingAdded() {
        if (isShowingKeyboard()) {
            closeKeyboard();
        }
        toolbar.setNavigationIcon(R.drawable.toolbar_action_undo);
        sendDrawingButton.setSolidBackgroundColor(getControllerFactory().getAccentColorController().getColor());
        sendDrawingButton.setClickable(true);
    }

    @Override
    public void drawingCleared() {
        toolbar.setNavigationIcon(R.drawable.toolbar_action_undo_disabled);
        int colorWithAlpha = ColorUtils.injectAlpha(SEND_BUTTON_DISABLED_ALPHA, getControllerFactory().getAccentColorController().getColor());
        sendDrawingButton.setSolidBackgroundColor(colorWithAlpha);
        sendDrawingButton.setClickable(false);
    }

    @Override
    public void reserveBitmapMemory(int width, int height) {
        MemoryImageCache.reserveImageMemory(width, height);
    }

    @Override
    public void onScaleChanged(float scale) {
        sketchEditTextView.setSketchScale(scale);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) sketchEditTextView.getLayoutParams();
        clampSketchEditBoxPosition(params);
        sketchEditTextView.setLayoutParams(params);
    }

    @Override
    public void onScaleStart() {
        drawingCanvasView.hideText();
        sketchEditTextView.setAlpha(TEXT_ALPHA_VISIBLE);
    }

    @Override
    public void onScaleEnd() {
        closeKeyboard();
    }

    @Override
    public void onTextChanged(String text, int x, int y, float scale) {
        ViewUtils.setMarginLeft(sketchEditTextView, x);
        ViewUtils.setMarginTop(sketchEditTextView, y);
        sketchEditTextView.setSketchScale(scale);
        sketchEditTextView.setText(text);
        sketchEditTextView.setSelection(text.length());
    }

    @Override
    public void onTextRemoved() {
        sketchEditTextView.setText("");
        sketchEditTextView.removeListener(sketchEditTextListener);
        sketchEditTextView.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onKeyboardVisibilityChanged(boolean keyboardIsVisible, int keyboardHeight, View currentFocus) {
        if (!keyboardIsVisible) {
            closeKeyboard();
        }
        if (shouldOpenEditText) {
            shouldOpenEditText = false;
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) sketchEditTextView.getLayoutParams();
            params.leftMargin = (((ViewGroup) sketchEditTextView.getParent()).getMeasuredWidth() - sketchEditTextView.getMeasuredWidth()) / 2;
            params.topMargin = ((ViewGroup) sketchEditTextView.getParent()).getMeasuredHeight() - keyboardHeight - sketchEditTextView.getMeasuredHeight();
            sketchEditTextView.setLayoutParams(params);

            //Posted so that the user doesn't see the previous location
            getView().post(new Runnable() {
                @Override
                public void run() {
                    sketchEditTextView.setBackground(ColorUtils.getRoundedTextBoxBackground(getContext(), currentBackgroundColor, sketchEditTextView.getMeasuredHeight()));
                    sketchEditTextView.setAlpha(TEXT_ALPHA_VISIBLE);
                    sketchEditTextView.addListener(sketchEditTextListener);
                }
            });
        } else {
            sketchEditTextView.setAlpha(TEXT_ALPHA_VISIBLE);
        }

    }

    private void drawSketchEditText() {
        if (sketchEditTextView.getVisibility() == View.VISIBLE) {
            sketchEditTextView.setAlpha(TEXT_ALPHA_VISIBLE);
            sketchEditTextView.setCursorVisible(false);
            //This has to be on a post otherwise the setAlpha and setCursor won't be noticeable in the drawing cache
            getView().post(new Runnable() {
                @Override
                public void run() {
                    sketchEditTextView.setDrawingCacheEnabled(true);
                    Bitmap bitmapDrawingCache = sketchEditTextView.getDrawingCache();
                    if (bitmapDrawingCache != null) {
                        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) sketchEditTextView.getLayoutParams();
                        drawingCanvasView.showText();
                        drawingCanvasView.drawTextBitmap(
                            bitmapDrawingCache.copy(bitmapDrawingCache.getConfig(), true),
                            params.leftMargin,
                            params.topMargin,
                            sketchEditTextView.getText().toString(),
                            sketchEditTextView.getSketchScale());
                    } else {
                        drawingCanvasView.drawTextBitmap(null, 0, 0, "", 1.0f);
                    }
                    sketchEditTextView.setDrawingCacheEnabled(false);
                    sketchEditTextView.setAlpha(TEXT_ALPHA_INVISIBLE);
                }
            });
        }
    }

    private void clampSketchEditBoxPosition(FrameLayout.LayoutParams params) {
        int sketchWidth = (((ViewGroup) sketchEditTextView.getParent()).getMeasuredWidth());
        int sketchHeight = (((ViewGroup) sketchEditTextView.getParent()).getMeasuredHeight());
        int textWidth = sketchEditTextView.getMeasuredWidth();
        int textHeight = sketchEditTextView.getMeasuredHeight();

        params.leftMargin = Math.min(params.leftMargin, sketchWidth - textWidth / 2);
        params.topMargin = Math.min(params.topMargin, sketchHeight - textHeight / 2);
        params.leftMargin = Math.max(params.leftMargin, -textWidth / 2);
        params.topMargin = Math.max(params.topMargin, -textHeight / 2);
    }

    public interface Container { }
}
