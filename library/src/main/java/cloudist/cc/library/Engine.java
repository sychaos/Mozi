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
    private int computeSize() {
        // 强行变成偶数（不要问我为啥子）
        srcWidth = srcWidth % 2 == 1 ? srcWidth + 1 : srcWidth;
        srcHeight = srcHeight % 2 == 1 ? srcHeight + 1 : srcHeight;

        //得到长边，短边
        int longSide = Math.max(srcWidth, srcHeight);
        int shortSide = Math.min(srcWidth, srcHeight);

        //scale 比例
        float scale = ((float) shortSide / longSide);
        // 这部分看不懂 不知道具体的几个值有什么含义 返回的是采样率 TODO
        if (scale <= 1 && scale > 0.5625) {
            if (longSide < 1664) {
                return 1;
            } else if (longSide >= 1664 && longSide < 4990) {
                return 2;
            } else if (longSide > 4990 && longSide < 10240) {
                return 4;
            } else {
                return longSide / 1280 == 0 ? 1 : longSide / 1280;
            }
        } else if (scale <= 0.5625 && scale > 0.5) {
            return longSide / 1280 == 0 ? 1 : longSide / 1280;
        } else {
            return (int) Math.ceil(longSide / (1280.0 / scale));
        }
    }

    private ByteArrayOutputStream computeImage(Bitmap bitmap, ByteArrayOutputStream stream) {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
        return stream;
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


    // 其实我是不太懂为什么要旋转图片的
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

    private Bitmap scaleImage(Bitmap bitmap) {
        if (maxWidth < 0 || maxHeight < 0) {
            return bitmap;
        }
        int actualHeight = srcHeight;
        int actualWidth = srcWidth;

        //计算图片的宽高比
        float imgRatio = (float) actualWidth / actualHeight;
        //计算输出图片的最大宽高比
        float maxRatio = (float) maxWidth / maxHeight;
        //width and height values are set maintaining the aspect ratio of the image
        //如果原图比输出图片的高或者宽大，重新调整输出bitmap的宽高
        //如果原图小，这直接输出原图的宽高
        if (actualHeight > maxHeight || actualWidth > maxWidth) {
            if (imgRatio < maxRatio) {
                imgRatio = (float) maxHeight / actualHeight;
                actualWidth = (int) (imgRatio * actualWidth);
                actualHeight = (int) maxHeight;
            } else if (imgRatio > maxRatio) {
                imgRatio = (float) maxWidth / actualWidth;
                actualHeight = (int) (imgRatio * actualHeight);
                actualWidth = (int) maxWidth;
            } else {
                actualHeight = (int) maxHeight;
                actualWidth = (int) maxWidth;
            }
            Bitmap scaleBitmap = Bitmap.createBitmap(actualWidth, actualHeight, Bitmap.Config.ARGB_8888);
            float ratioX = actualWidth / (float) srcWidth;
            float ratioY = actualHeight / (float) srcHeight;
            Matrix scaleMatrix = new Matrix();
            scaleMatrix.setScale(ratioX, ratioY, 0, 0);
            // 1.跟下面是一样的效果 根据矩阵数据进行新bitmap的创建 主要差别在于bitmapConfig的设置
            Canvas canvas = new Canvas(scaleBitmap);
            canvas.setMatrix(scaleMatrix);
            canvas.drawBitmap(bitmap, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
            // 缩放法压缩
            return scaleBitmap;
        } else {
            return bitmap;
        }
    }

    File compress() throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = computeSize();

        // 采样率压缩
        Bitmap tagBitmap = BitmapFactory.decodeFile(srcImg, options);
        // 缩放法压缩
        tagBitmap = scaleImage(tagBitmap);
        // 旋转图片
        tagBitmap = rotatingImage(tagBitmap);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        // 质量压缩
        computeImage(tagBitmap, stream);
        //  通过缩放法和质量压缩法循环压缩 以缩放法缩减至0.3，质量压缩至0.4为极限
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

    public Engine setMaxWidthAndHeight(int maxWidth, int maxHeight) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        return this;
    }

    public Engine setIdealMaxSize(int idealMaxSize) {
        this.idealMaxSize = idealMaxSize;
        return this;
    }
}