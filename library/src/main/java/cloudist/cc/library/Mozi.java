package cloudist.cc.library;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by cloudist on 2017/10/31.
 */

public class Mozi {

    private static final String DEFAULT_DISK_CACHE_DIR = "bitmap_compress_disk_cache";

    private Context context;
    /**
     * 临时文件夹路径
     */
    private String mTargetDir;
    private List<String> mPaths;

    private int maxWidth = -1;
    private int maxHeight = -1;
    private int idealMaxSize = -1;

    private Mozi(Builder builder) {
        this.mPaths = builder.mPaths;
        this.mTargetDir = builder.mTargetDir;
        this.maxWidth = builder.maxWidth;
        this.maxHeight = builder.maxHeight;
        this.idealMaxSize = builder.maxSize;
    }

    public static Builder with(Context context) {
        return new Builder(context);
    }

    /**
     * start compress and return the mFile
     */
    @WorkerThread
    private File get(String path, Context context) throws IOException {
        File file = getImageCacheFile(context, path, Checker.checkSuffix(path),
                maxWidth, maxHeight, idealMaxSize);
        if (file.exists()) {
            // 如果缓存文件已经存在直接返回
            return file;
        } else {
            return new Engine(path, file)
                    .setIdealMaxSize(idealMaxSize)
                    .setMaxHeight(maxHeight)
                    .setMaxWidth(maxWidth)
                    .compress();
        }
    }

    @WorkerThread
    private List<File> get(Context context) throws IOException {
        List<File> results = new ArrayList<>();
        Iterator<String> iterator = mPaths.iterator();

        // 循环
        while (iterator.hasNext()) {
            String path = iterator.next();
            // 通过后缀名判断是否为图片
            if (Checker.isImage(path)) {
                File file = getImageCacheFile(context, path, Checker.checkSuffix(path),
                        maxWidth, maxHeight, idealMaxSize);
                if (file.exists()) {
                    results.add(file);
                } else {
                    // getImageCacheFile(context, Checker.checkSuffix(path) 获取缓存该图片的文件路径
                    // 可以考虑多线程 TODO 其实没啥必要主要是针对idealSize很小的情况下会变慢
                    results.add(new Engine(path, file)
                            .setIdealMaxSize(idealMaxSize)
                            .setMaxHeight(maxHeight)
                            .setMaxWidth(maxWidth)
                            .compress());
                }
            }
            iterator.remove();
        }

        return results;
    }

    @WorkerThread
    private void clear(Context context) {
        if (TextUtils.isEmpty(mTargetDir)) {
            // 缓存图片的文件夹
            mTargetDir = getImageCacheDir(context).getAbsolutePath();
        }
        File cacheDir = new File(mTargetDir);
        // 不存在则返回
        if (!cacheDir.exists()) {
            return;
        }
        // 删除
        File[] files = cacheDir.listFiles();
        if (cacheDir.isDirectory() && files.length != 0) {
            for (File file : files) {
                file.delete();
            }
        }
    }

    /**
     * Returns a mFile with a cache audio name in the private cache directory.
     *
     * @param context A context.
     */
    private File getImageCacheFile(Context context, String path, String suffix, int maxWidth, int maxHeight, int idealMaxSize) {
        if (TextUtils.isEmpty(mTargetDir)) {
            // 生成一个缓存图片的文件夹
            mTargetDir = getImageCacheDir(context).getAbsolutePath();
        }

        String cacheBuilder = mTargetDir + "/" +
                "#W" + maxWidth +
                "#H" + maxHeight +
                "#S" + idealMaxSize +
                path.hashCode() +
                (TextUtils.isEmpty(suffix) ? ".jpg" : suffix);

        return new File(cacheBuilder);
    }

    /**
     * Returns a directory with a default name in the private cache directory of the application to
     * use to store retrieved audio.
     *
     * @param context A context.
     * @see #getImageCacheDir(Context, String)
     */
    @Nullable
    private File getImageCacheDir(Context context) {
        return getImageCacheDir(context, DEFAULT_DISK_CACHE_DIR);
    }

    /**
     * Returns a directory with the given name in the private cache directory of the application to
     * use to store retrieved media and thumbnails.
     *
     * @param context   A context.
     * @param cacheName The name of the subdirectory in which to store the cache.
     * @see #getImageCacheDir(Context)
     */
    @Nullable
    private File getImageCacheDir(Context context, String cacheName) {
        File cacheDir = context.getExternalCacheDir();
        if (cacheDir != null) {
            File result = new File(cacheDir, cacheName);
            if (!result.mkdirs() && (!result.exists() || !result.isDirectory())) {
                // File wasn't able to create a directory, or the result exists but not a directory
                return null;
            }
            return result;
        }
        return null;
    }

    public static class Builder {
        private Context context;
        private String mTargetDir;
        private List<String> mPaths;
        private int maxWidth = -1;
        private int maxHeight = -1;
        private int maxSize = -1;

        Builder(Context context) {
            this.context = context;
            this.mPaths = new ArrayList<>();
        }

        private Mozi build() {
            return new Mozi(this);
        }

        public Builder load(File file) {
            this.mPaths.add(file.getAbsolutePath());
            return this;
        }

        public Builder load(String string) {
            this.mPaths.add(string);
            return this;
        }

        public Builder load(List<String> list) {
            this.mPaths.addAll(list);
            return this;
        }

        public Builder putGear(int gear) {
            return this;
        }

        public Builder setTargetDir(String targetDir) {
            this.mTargetDir = targetDir;
            return this;
        }

        public Builder setMaxWidth(int maxWidth) {
            this.maxWidth = maxWidth;
            return this;
        }

        public Builder setMaxHeight(int maxHeight) {
            this.maxHeight = maxHeight;
            return this;
        }

        // 设置压缩后图片的最大大小 单位k
        public Builder setMaxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public File get(String path) throws IOException {
            return build().get(path, context);
        }

        /**
         * begin compress image with synchronize
         *
         * @return the thumb image file list
         */
        public List<File> get() throws IOException {
            return build().get(context);
        }

        /**
         * begin clear image with synchronize
         *
         * @return the thumb image file list
         */
        public void clear() {
            build().clear(context);
        }
    }


}
