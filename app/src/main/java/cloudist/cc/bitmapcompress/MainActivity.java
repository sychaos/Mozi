package cloudist.cc.bitmapcompress;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cloudist.cc.library.KeyNorm;
import cloudist.cc.library.Mozi;
import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import me.iwf.photopicker.PhotoPicker;

public class MainActivity extends AppCompatActivity {

    private List<ImageBean> mImageList = new ArrayList<>();
    private ImageAdapter mAdapter = new ImageAdapter(mImageList);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RecyclerView mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setAdapter(mAdapter);

        Button fab = (Button) findViewById(R.id.fab);
        Button clear = (Button) findViewById(R.id.clear);
        Button getFile = (Button) findViewById(R.id.getFile);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PhotoPicker.builder()
                        .setPhotoCount(9)
                        .setShowCamera(true)
                        .setShowGif(true)
                        .setPreviewEnabled(false)
                        .start(MainActivity.this, 10300);
            }
        });
        getFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PhotoPicker.builder()
                        .setPhotoCount(9)
                        .setShowCamera(true)
                        .setShowGif(true)
                        .setPreviewEnabled(false)
                        .start(MainActivity.this, 10301);
            }
        });
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Mozi.with(MainActivity.this).clear();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == 10300) {
                if (data != null) {
                    mImageList.clear();

                    ArrayList<String> photos = data.getStringArrayListExtra(PhotoPicker.KEY_SELECTED_PHOTOS);
                    compressWithRx(photos);
                }
            }
            if (requestCode == 10301) {
                if (data != null) {
                    mImageList.clear();
                    ArrayList<String> photos = data.getStringArrayListExtra(PhotoPicker.KEY_SELECTED_PHOTOS);
                    File file = Mozi.with(MainActivity.this).getFile(photos.get(0) + "玩个毛");
                    Toast.makeText(MainActivity.this, file.exists() + "", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void compressWithRx(final List<String> photos) {
        Flowable.just(photos)
                .observeOn(Schedulers.io())
                .map(new Function<List<String>, List<File>>() {
                    @Override
                    public List<File> apply(@NonNull final List<String> list) throws Exception {
                        return Mozi.with(MainActivity.this).load(list).get(new KeyNorm() {
                            @Override
                            public String nameRule(int index) {
                                return list.get(index) + "玩个毛";
                            }
                        });
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<List<File>>() {
                    @Override
                    public void accept(@NonNull List<File> list) throws Exception {
                        for (File file : list) {
                            showResult(photos, file);
                        }
                    }
                });
    }

    private void showResult(List<String> photos, File file) {
        int[] originSize = computeSize(photos.get(mAdapter.getItemCount()));
        int[] thumbSize = computeSize(file.getAbsolutePath());
        String originArg = String.format(Locale.CHINA, "原图参数：%d*%d, %dk", originSize[0], originSize[1], new File(photos.get(mAdapter.getItemCount())).length() >> 10);
        String thumbArg = String.format(Locale.CHINA, "压缩后参数：%d*%d, %dk", thumbSize[0], thumbSize[1], file.length() >> 10);

        ImageBean imageBean = new ImageBean(originArg, thumbArg, file.getAbsolutePath());
        mImageList.add(imageBean);
        mAdapter.notifyDataSetChanged();
    }

    private int[] computeSize(String srcImg) {
        int[] size = new int[2];

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        options.inSampleSize = 1;

        BitmapFactory.decodeFile(srcImg, options);
        size[0] = options.outWidth;
        size[1] = options.outHeight;

        return size;
    }
}
