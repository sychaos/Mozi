package cloudist.cc.library;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Responsible for starting compress and managing active and cached resources.
 */
class Engine {
    private ExifInterface srcExif;
    private String srcImg;
    private File tagImg;
    private int srcWidth;
    private int srcHeight;

    private int maxWidth = -1;
    private int maxHeight = -1;
    private int idealMaxSize = -1;

    //  srcImg 原图路径
    //  tagImg 缓存图片路径
    Engine(String srcImg, File tagImg) throws IOException {
        if (Checker.isJPG(srcImg)) {
            this.srcExif = new ExifInterface(srcImg);
        }
        this.tagImg = tagImg;
        this.srcImg = srcImg;

        BitmapFactory.Options options = new BitmapFactory.Options();
        // inJustDecodeBounds只生成宽高 bitmap为空
        options.inJustDecodeBounds = true;
        // 默认采样率
        options.inSampleSize = 1;
        BitmapFactory.decodeFile(srcImg, options);
        //获取原图的长宽 以便计算采样率和缩小图片
        this.srcWidth = options.outWidth;
        this.srcHeight = options.outHeight;
    }

    // 采样率计算采样率
    private int computeNormalSize() {
        // 强行变成偶数（不要问我为啥子）
        srcWidth = srcWidth % 2 == 1 ? srcWidth + 1 : srcWidth;
        srcHeight = srcHeight % 2 == 1 ? srcHeight + 1 : srcHeight;

        //得到长边，短边
        int longSide = Math.max(srcWidth, srcHeight);
        int shortSide = Math.min(srcWidth, srcHeight);

        //scale 比例
        // TODO 其实我是看不懂为什么用1664，4990和10240的
        float scale = ((float) shortSide / longSide);
        //[1, 0.5625) 即图片处于 [1:1 ~ 9:16)
        if (scale <= 1 && scale > 0.5625) {
            // 且长边小于1664 采样率为1
            if (longSide < 1664) {
                return 1;
                // 且长边在[1664, 4990) 采样率为2
            } else if (longSide >= 1664 && longSide < 4990) {
                return 2;
                // 且长边在[4990, 10240) 采样率为4
            } else if (longSide >= 4990 && longSide < 10240) {
                return 4;
            } else {
                return longSide / 1280 == 0 ? 1 : longSide / 1280;
            }
            //  [0.5625, 0.5) 即图片处于 [9:16 ~ 1:2) 比例范围内
        } else if (scale <= 0.5625 && scale > 0.5) {
            return longSide / 1280 == 0 ? 1 : longSide / 1280;
            //  [0.5, 0) 即图片处于 [1:2 ~ 1:∞) 比例范围内
        } else {
            return (int) Math.ceil(longSide / (1280.0 / scale));
        }
    }

    private int computeScaleSize() {
        //计算图片的宽高比
        float imgRatio = (float) srcWidth / srcHeight;

        if (maxWidth <= 0) {
            maxWidth = (int) (maxHeight * imgRatio);
        } else if (maxHeight <= 0) {
            maxHeight = (int) (maxWidth / imgRatio);
        }

        int inSampleSize = 1;
        // 调整采样率 如果 输出图片宽高比原图大 用比例较大的值作为采样率
        if (srcHeight > maxHeight || srcWidth > maxWidth) {
            final int heightRatio = Math.round((float) srcHeight / (float) maxHeight);
            final int widthRatio = Math.round((float) srcWidth / (float) maxWidth);
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }

        final float totalPixels = srcWidth * srcHeight;
        final float totalReqPixelsCap = maxWidth * maxHeight * 2;
        //  采样率如果不合适的话 +1
        while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
            inSampleSize++;
        }

        return inSampleSize;
    }

    private Bitmap compressNormalBitmap() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        // 这是一套方法 start
        options.inSampleSize = computeNormalSize();
        // 采样率压缩
        return BitmapFactory.decodeFile(srcImg, options);
    }

    private Bitmap compressScaleBitmap() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        // 这是一套方法 start
        options.inSampleSize = computeScaleSize();
        // 采样率压缩
        Bitmap bmp = BitmapFactory.decodeFile(srcImg, options);
        // 缩放法压缩
        Bitmap scaledBitmap = Bitmap.createBitmap(maxWidth, maxHeight, Bitmap.Config.ARGB_8888);

        float ratioX = maxWidth / (float) options.outWidth;
        float ratioY = maxHeight / (float) options.outHeight;

        Matrix scaleMatrix = new Matrix();
        scaleMatrix.setScale(ratioX, ratioY, 0, 0);

        // 根据矩阵数据进行新bitmap的创建
        assert scaledBitmap != null;
        Canvas canvas = new Canvas(scaledBitmap);
        canvas.setMatrix(scaleMatrix);
        assert bmp != null;
        canvas.drawBitmap(bmp, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));

        return scaledBitmap;
    }

    // 其实我是不太懂为什么要旋转图片的，但是事实证明这个方法必须用。。。。
    private Bitmap rotatingImage(Bitmap bitmap) {
        if (srcExif == null) return bitmap;

        Matrix matrix = new Matrix();
        int orientation = srcExif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private void cyclicCompute(Bitmap bitmap, ByteArrayOutputStream stream) {
        int quality = 80;
        float ratio = 0.9f;
        Matrix scaleMatrix = new Matrix();
        while (idealMaxSize > 0 && stream.toByteArray().length > idealMaxSize * 1024 && ratio > 0.3) {
            scaleMatrix.setScale(ratio, ratio, 0, 0);
            Bitmap scaleBitmap = Bitmap.createBitmap((int) (bitmap.getWidth() * ratio), (int) (bitmap.getHeight() * ratio),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(scaleBitmap);
            canvas.setMatrix(scaleMatrix);
            canvas.drawBitmap(bitmap, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
            ratio = ratio - 0.1f;
            //当质量大于40时进行质量压缩
            if (quality >= 40) {
                quality = quality - 10;
            }
            stream.reset();
            scaleBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream);
        }
    }

    private ByteArrayOutputStream computeImage(Bitmap bitmap, ByteArrayOutputStream stream) {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
        return stream;
    }

    File compress() throws IOException {

        Bitmap tagBitmap;

        if (maxHeight < 0 && maxWidth < 0) {
            tagBitmap = compressNormalBitmap();
        } else {
            tagBitmap = compressScaleBitmap();
        }
        // 旋转图片
        tagBitmap = rotatingImage(tagBitmap);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        // 质量压缩
        computeImage(tagBitmap, stream);
        // 通过缩放法和质量压缩法循环压缩 以缩放法缩减至0.3，质量压缩至0.4为极限
        cyclicCompute(tagBitmap, stream);

        tagBitmap.recycle();

        // 写到缓存的路径下
        FileOutputStream fos = new FileOutputStream(tagImg);
        fos.write(stream.toByteArray());
        fos.flush();
        fos.close();
        stream.close();
        return tagImg;
    }

    public Engine setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
        return this;
    }

    public Engine setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
        return this;
    }

    public Engine setIdealMaxSize(int idealMaxSize) {
        this.idealMaxSize = idealMaxSize;
        return this;
    }
}