# Mozi
图片压缩,支持限制最大宽高，及最大图片大小 名字来源于中学课本，毕竟这个库脱胎于鲁班，哈哈

## ScreenShot

<img src="display/screenshot_1.png" width = "270" height = "480" alt="screenshot_1" align=center />     <img src="display/screenshot_2.png" width = "270" height = "480" alt="screenshot_2" align=center />

## Usage

Step 1. Add it in your root build.gradle at the end of repositories:

	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}

Step 2. Add the dependency

	dependencies {
	        compile 'com.github.sychaos:Mozi:1.0.1'
	}

## Sample Code
``` xml
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

```Java
    Mozi.with(MainActivity.this)
        .setMaxSize(maxSize)
        .setMaxWidthAndHeight(width,height)
        .load(list).get();
```
或者
```Java
    Mozi.with(MainActivity.this)
        .get(path);
```
两者都要写在子线程中

## Thanks
[Luban](https://github.com/Curzibn/Luban) 

[CompressHelper](https://github.com/nanchen2251/CompressHelper) 
