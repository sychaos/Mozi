# Mozi
图片压缩,支持限制最大宽高，及最大图片大小 名字来源于中学课本，毕竟这个库脱胎于鲁班，哈哈

## Sample Code
```Java
    Mozi.with(MainActivity.this)
        .setMaxSize(200)
        .load(list).get();
```
或者
```Java
    Mozi.with(MainActivity.this)
        .setMaxSize(maxSize)
        .setMaxWidthAndHeight(width,height)
        .load(list).get();
        
    Mozi.with(MainActivity.this)
        .get(path);
```

## Thanks
[Luban](https://github.com/Curzibn/Luban) 

[CompressHelper](https://github.com/nanchen2251/CompressHelper) 
