package vedipen.memeapp.activity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ToggleButton;

import com.flipboard.bottomsheet.BottomSheetLayout;
import com.flipboard.bottomsheet.OnSheetDismissedListener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnTextChanged;
import vedipen.memeapp.App;
import vedipen.memeapp.R;
import vedipen.memeapp.data.MemeFont;
import vedipen.memeapp.data.MemeLibConfig;
import vedipen.memeapp.data.MemeSetting;
import vedipen.memeapp.ui.FontAdapter;
import vedipen.memeapp.util.Helpers;
import uz.shift.colorpicker.LineColorPicker;

/**
 * Activity for creating memes
 */
public class MemeCreateActivity extends AppCompatActivity
        implements MemeSetting.OnMemeSettingChangedListener<Typeface, Bitmap> {
    //########################
    //## Static
    //########################
    public final static int RESULT_MEME_EDITING_FINISHED = 150;
    public final static int RESULT_MEME_EDIT_SAVED = 1;
    public final static int RESULT_MEME_NOT_SAVED = 0;
    public final static String EXTRA_IMAGE_PATH = "extraImage";
    public final static String ASSET_IMAGE = "assetImage";

    //########################
    //## UI Binding
    //########################
//    @BindView(R.id.fab)
//    FloatingActionButton fab;

    @BindView(R.id.toolbar)
    Toolbar toolbar;

//    @BindView(R.id.memecreate__activity__bottomsheet_layout)
//    BottomSheetLayout bottomSheet;

    @BindView(R.id.memecreate__activity__image)
    ImageView imageEditView;

    @BindView(R.id.memecreate__activity__edit_caption_bottom)
    EditText textEditBottomCaption;

    @BindView(R.id.memecreate__activity__edit_caption_top)
    EditText textEditTopCaption;

    //#####################
    //## Members
    //#####################
    private static boolean doubleBackToExitPressedOnce = false;
    private Bitmap lastBitmap = null;
    private long memeSavetime = -1;
    private App app;
    private MemeSetting<Typeface, Bitmap> memeSetting;
    private boolean bFullscreenImage = true;

    //#####################
    //## Methods
    //#####################
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.memecreate__activity);

        // Quit activity if no image was given
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        if (!(Intent.ACTION_SEND.equals(action) && type.startsWith("image/")) &&
                (!getIntent().hasExtra(EXTRA_IMAGE_PATH) || !getIntent().hasExtra(ASSET_IMAGE))) {
            finish();
            return;
        }

        // Bind Ui
        ButterKnife.bind(this);
        app = (App) getApplication();

        // Set toolbar
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }


        Bitmap bitmap = extractBitmapFromIntent(intent);
        memeSetting = new MemeSetting<>(app.getFonts().get(app.settings.getLastSelectedFont()), bitmap);
        memeSetting.setDisplayImage(memeSetting.getImage().copy(Bitmap.Config.RGB_565, false));
        memeSetting.setFontId(app.settings.getLastSelectedFont());

        textEditTopCaption.setText(memeSetting.getCaptionTop());
        textEditBottomCaption.setText(memeSetting.getCaptionBottom());

        memeSetting.setMemeSettingChangedListener(this);
        memeSetting.notifyChangedListener();

    }

    @Override
    protected void onDestroy() {
        imageEditView.setImageBitmap(null);
        if (lastBitmap != null && !lastBitmap.isRecycled())
            lastBitmap.recycle();
        if (!memeSetting.getImage().isRecycled())
            memeSetting.getImage().recycle();
        if (!memeSetting.getDisplayImage().isRecycled())
            memeSetting.getDisplayImage().recycle();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bFullscreenImage) {
            bFullscreenImage = false;
            overridePendingTransition(R.anim.fadein, R.anim.fadeout);
        }
    }

    private Bitmap extractBitmapFromIntent(final Intent intent) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        Bitmap bitmap = null;
        String imagePath = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
        App.log("imagepath::" + imagePath);
        if (intent.getAction() != null && intent.getAction().equals(Intent.ACTION_SEND) && intent.getType().startsWith("image/")) {
            Uri imageURI = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (imageURI != null) {
                try {
                    bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageURI);
                } catch (IOException e) {
                    bitmap = null;
                    e.printStackTrace();
                }
            }
        } else if (intent.getBooleanExtra(ASSET_IMAGE, false)) {
            try {
                //Scale big images down to avoid "out of memory"
                InputStream inputStream = getAssets().open(imagePath);
                BitmapFactory.decodeStream(inputStream, new Rect(0, 0, 0, 0), options);
                options.inSampleSize = Helpers.calculateInSampleSize(options, app.settings.getRenderQuality());
                options.inJustDecodeBounds = false;
                inputStream.close();
                inputStream = getAssets().open(imagePath);
                bitmap = BitmapFactory.decodeStream(inputStream, new Rect(0, 0, 0, 0), options);
            } catch (IOException e) {
                bitmap = null;
                e.printStackTrace();
            }
        } else {
            //Scale big images down to avoid "out of memory"
            BitmapFactory.decodeFile(imagePath, options);
            options.inSampleSize = Helpers.calculateInSampleSize(options, app.settings.getRenderQuality());
            options.inJustDecodeBounds = false;
            bitmap = BitmapFactory.decodeFile(imagePath, options);
        }
        return bitmap;
    }

    @Override
    public void onBackPressed() {
//        if (bottomSheet.isSheetShowing()) {
//            bottomSheet.dismissSheet();
//            return;
//        }
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }
        doubleBackToExitPressedOnce = true;
        Snackbar.make(findViewById(android.R.id.content), R.string.creator__press_back_again_to_exit, Snackbar.LENGTH_SHORT).show();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                doubleBackToExitPressedOnce = false;
            }
        }, 2000);
    }

    @OnClick(R.id.memecreate__activity__image)
    public void onImageClicked(View view) {
        Helpers.hideSoftKeyboard(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.creatememe__menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_share: {
                app.shareBitmapToOtherApp(lastBitmap, this);
                return true;
            }
            case R.id.action_save: {
                saveMemeToFilesystem();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void saveMemeToFilesystem() {
        String filepath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), getString(R.string.app_name)).getAbsolutePath();
        String thumbnailPath = new File(filepath, getString(R.string.dot_thumbnails)).getAbsolutePath();
        if (memeSavetime < 0) {
            memeSavetime = System.currentTimeMillis();
        }

        String filename = String.format(Locale.getDefault(), "%s_%d.jpg", getString(R.string.app_name), memeSavetime);
        if (Helpers.saveBitmapToFile(filepath, filename, lastBitmap) != null && Helpers.saveBitmapToFile(thumbnailPath, filename, Helpers.createThumbnail(lastBitmap)) != null) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            dialog.setTitle(R.string.creator__saved_successfully)
                    .setMessage(R.string.creator__saved_successfully_message)
                    .setNegativeButton(R.string.creator__no_keep_editing, null)
                    .setPositiveButton(R.string.main__yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    });
            dialog.show();
        }
    }

//    @OnClick(R.id.fab)
//    public void onFloatingButtonClicked(View view) {
//        fab.setVisibility(View.INVISIBLE);
//        bottomSheet.showWithSheetView(((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE)).
//                inflate(R.layout.memecreate__bottom_sheet, bottomSheet, false));
//        bottomSheet.addOnSheetStateChangeListener(this);
//        bottomSheet.addOnSheetDismissedListener(this);
//
//        LineColorPicker colorPickerShade = ButterKnife.findById(this, R.id.memecreate__bottom_sheet__color_picker_for_border);
//        LineColorPicker colorPickerText = ButterKnife.findById(this, R.id.memecreate__bottom_sheet__color_picker_for_text);
//        Spinner dropdownFont = ButterKnife.findById(this, R.id.memecreate__bottom_sheet__dropdown_font);
//        SeekBar seekFontSize = ButterKnife.findById(this, R.id.memecreate__bottom_sheet__seek_font_size);
//        ToggleButton toggleAllCaps = ButterKnife.findById(this, R.id.memecreate__bottom_sheet__toggle_all_caps);
//
//        colorPickerText.setColors(MemeLibConfig.MEME_COLORS.ALL);
//        colorPickerShade.setColors(MemeLibConfig.MEME_COLORS.ALL);
//
//        FontAdapter adapter = new FontAdapter(
//                this, android.R.layout.simple_list_item_1, app.getFonts());
//        dropdownFont.setAdapter(adapter);
//
//
//        // Apply existing settings
//        colorPickerText.setSelectedColor(memeSetting.getTextColor());
//        colorPickerShade.setSelectedColor(memeSetting.getBorderColor());
//        dropdownFont.setSelection(memeSetting.getFontId());
//        toggleAllCaps.setChecked(memeSetting.isAllCaps());
//        ((SeekBar) ButterKnife.findById(this, R.id.memecreate__bottom_sheet__seek_font_size)).setProgress(memeSetting.getFontSize() - MemeLibConfig.FONT_SIZES.MIN);
//
//        //
//        //  Add bottom sheet listeners
//        //
//        colorPickerShade.setOnClickListener(new View.OnClickListener() {
//            public void onClick(View v) {
//                LineColorPicker picker = (LineColorPicker) v;
//                memeSetting.setBorderColor(picker.getColor());
//            }
//        });
//        colorPickerText.setOnClickListener(new View.OnClickListener() {
//            public void onClick(View v) {
//                LineColorPicker picker = (LineColorPicker) v;
//                memeSetting.setTextColor(picker.getColor());
//            }
//        });
//        dropdownFont.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            public void onNothingSelected(AdapterView<?> parent) {
//            }
//
//            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//                memeSetting.setFont((MemeFont<Typeface>) parent.getSelectedItem());
//                memeSetting.setFontId(parent.getSelectedItemPosition());
//                app.settings.setLastSelectedFont(memeSetting.getFontId());
//            }
//        });
//        seekFontSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
//            public void onStartTrackingTouch(SeekBar seekBar) {
//            }
//
//            public void onStopTrackingTouch(SeekBar seekBar) {
//            }
//
//            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//                memeSetting.setFontSize(progress + MemeLibConfig.FONT_SIZES.MIN);
//            }
//        });
//        toggleAllCaps.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
//            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//                memeSetting.setAllCaps(isChecked);
//            }
//        });
//    }

    public Bitmap drawMultilineTextToBitmap(Context c, MemeSetting<Typeface, Bitmap> memeSetting) {
        // prepare canvas
        Resources resources = c.getResources();
        Bitmap bitmap = memeSetting.getDisplayImage();

        float scale = Helpers.getScalingFactor(bitmap.getWidth(), bitmap.getHeight());
        float borderScale = scale * memeSetting.getFontSize() / MemeLibConfig.FONT_SIZES.DEFAULT;
        Bitmap.Config bitmapConfig = bitmap.getConfig();
        // set default bitmap config if none
        if (bitmapConfig == null) {
            bitmapConfig = Bitmap.Config.RGB_565;
        }
        // resource bitmaps are immutable,
        // so we need to convert it to mutable one
        bitmap = bitmap.copy(bitmapConfig, true);
        Canvas canvas = new Canvas(bitmap);

        // new antialiased Paint
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize((int) (memeSetting.getFontSize() * scale));
        paint.setTypeface(memeSetting.getFont().getFont());
        //paint.setStrokeWidth(memeSetting.getFontSize() / 4);
        paint.setStrokeWidth(borderScale);

        String[] textStrings = {memeSetting.getCaptionTop(), memeSetting.getCaptionBottom()};
        if (memeSetting.isAllCaps()) {
            for (int i = 0; i < textStrings.length; i++) {
                textStrings[i] = textStrings[i].toUpperCase();
            }
        }

        for (int i = 0; i < textStrings.length; i++) {
            paint.setColor(memeSetting.getBorderColor());
            paint.setStyle(Paint.Style.FILL_AND_STROKE);

            // set text width to canvas width minus 16dp padding
            int textWidth = canvas.getWidth() - (int) (16 * scale);

            // init StaticLayout for text
            StaticLayout textLayout = new StaticLayout(
                    textStrings[i], paint, textWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);

            // get height of multiline text
            int textHeight = textLayout.getHeight();

            // get position of text's top left corner  center: (bitmap.getWidth() - textWidth)/2
            float x = (bitmap.getWidth() - textWidth) / 2;
            float y = 0;
            if (i == 0)
                y = bitmap.getHeight() / 15;
            else
                y = bitmap.getHeight() - textHeight;

            // draw text to the Canvas center
            canvas.save();
            canvas.translate(x, y);
            textLayout.draw(canvas);

            // new antialiased Paint
            paint.setColor(memeSetting.getTextColor());
            paint.setStyle(Paint.Style.FILL);

            // init StaticLayout for text
            textLayout = new StaticLayout(
                    textStrings[i], paint, textWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);

            // get height of multiline text
            textHeight = textLayout.getHeight();

            // draw text to the Canvas center
            textLayout.draw(canvas);
            canvas.restore();
        }
        return bitmap;
    }

    @OnTextChanged(value = R.id.memecreate__activity__edit_caption_bottom, callback = OnTextChanged.Callback.TEXT_CHANGED)
    public void onCaptionBottomChanged(CharSequence text) {
        memeSetting.setCaptionBottom(text.toString());
    }

    @OnTextChanged(value = R.id.memecreate__activity__edit_caption_top, callback = OnTextChanged.Callback.TEXT_CHANGED)
    public void onCaptionTopChanged(CharSequence text) {
        memeSetting.setCaptionTop(text.toString());
    }

    @Override
    public void onMemeSettingChanged(MemeSetting<Typeface, Bitmap> memeSetting) {
        imageEditView.setImageBitmap(null);
        if (lastBitmap != null)
            lastBitmap.recycle();
        Bitmap bmp = drawMultilineTextToBitmap(this, memeSetting);
        imageEditView.setImageBitmap(bmp);
        lastBitmap = bmp;
    }

//    @Override
//    public void onSheetStateChanged(BottomSheetLayout.State state) {
//        if (state == BottomSheetLayout.State.HIDDEN) {
//            fab.setVisibility(View.VISIBLE);
//            textEditBottomCaption.setVisibility(View.VISIBLE);
//            textEditTopCaption.setVisibility(View.VISIBLE);
//        }
//        if (state == BottomSheetLayout.State.EXPANDED || state == BottomSheetLayout.State.PEEKED) {
//            textEditBottomCaption.setVisibility(View.GONE);
//            textEditTopCaption.setVisibility(View.GONE);
//        }
//    }
//
//    @Override
//    public void onDismissed(BottomSheetLayout bottomSheetLayout) {
//        fab.setVisibility(View.VISIBLE);
//    }
}
