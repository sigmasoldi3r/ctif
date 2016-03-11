# CTIF

An image format for OpenComputers and ComputerCraft. See releases for JAR converter binaries.

# Usage

To run CTIFConverter, you need Java 7 and a recent enough version of ImageMagick. If you don't have ImageMagick, install it from your package manager. If you're on 
Windows, unpacking the portable version of ImageMagick ([x86](http://www.imagemagick.org/download/binaries/ImageMagick-6.9.3-7-portable-Q16-x86.zip), 
[x64](http://www.imagemagick.org/download/binaries/ImageMagick-6.9.3-7-portable-Q16-x64.zip)) into your PATH, or the current working directory, *might* help.

    java -jar CTIFConverter.jar -h

will be your best friend. Here's an example command:

    java -jar CTIFConverter.jar -m oc -P preview.png -o image.ctif image.png

will convert image.png to image.ctif and kindly save preview.png as a preview file. (The "-P preview.png" can be omitted)

    java -jar CTIFConverter.jar -m cc -W 102 -H 57 -o image.ctif image.png

will convert your image into a ComputerCraft picture of at most 102x57. If you want to ignore the aspect ratio and force it to be 
exactly 102x57, use "-N".

# Viewers

If you just want to view CTIF files, see the viewers directory.

* ctif-cc.lua - ComputerCraft image viewer. "ctif-cc {file} [monitor side]" to use. If it errors about the image size being too 
large, keep in mind it operates on *characters*, while the converter operates on *pixels* - to convert from one to the other, 
multiply the width by 2 and the height by 3.

An OpenComputers viewer will be released as part of the promised-at-BTM15 release of OpenPoint soon.

# Compiling

If you want to make a JAR yourself, you need:

* JCommander 1.48+
* im4java 1.4.0+
