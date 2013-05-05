package tripod.colony;

import java.io.*;
import java.util.logging.Logger;
import java.util.logging.Level;

import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;;
import com.sun.media.jai.codec.*;

public class TIFFCodec implements TIFFTags {
    static final Logger logger = Logger.getLogger(TIFFCodec.class.getName());

    public static Raster decode (File file) throws IOException {
        ImageDecoder decoder = ImageCodec.createImageDecoder
	    ("TIFF", file, new TIFFDecodeParam ());

	int ndirs = decoder.getNumPages();
	TIFFDirectory tif = new TIFFDirectory (decoder.getInputStream(), 0);
	TIFFField[] fields = tif.getFields();

	double width = 0, height = 0;
	String unit = "";
	double xres = 0., yres = 0.;
        double rows = -1;
	int photometric = -1, bpp = -1;
	for (int j = 0; j < fields.length; ++j) {
	    TIFFField f = fields[j];
	    int tag = f.getTag();
            try {
                switch (tag) {
                case TAG_RESOLUTIONUNIT:
                    {
                        int u = f.getAsInt(0);
                        if (u == RESOLUTIONUNIT_NONE) {
                        }
                        else if (u == RESOLUTIONUNIT_INCH) {
                            unit = "in";
                        }
                        else if (u == RESOLUTIONUNIT_CENT) {
                            unit = "cm";
                        }
                    }
                    break;
                    
                case TAG_XRESOLUTION:
                    xres = f.getAsFloat(0);
                    break;
                    
                case TAG_YRESOLUTION:
                    yres = f.getAsFloat(0);
                    break;
                
                case TAG_ROWSPERSTRIP:
                    rows = f.getAsFloat(0);
                    break;
                    
                case TAG_PHOTOMETRIC:
                    photometric = f.getAsInt(0);
                    break;
                    
                case TAG_BITSPERSAMPLE:
                    bpp = f.getAsInt(0);
                    break;
                    
                case TAG_IMAGEWIDTH: 
                    width = f.getAsFloat(0);
                    break;
                    
                case TAG_IMAGELENGTH:	
                    height = f.getAsFloat(0);
                    break;
                }
            }
            catch (Exception ex) {
                logger.warning("## TIFF decoder tag="
                               +tag+"; "+ex.getMessage());
            }
	}

	/*
          if (xres > 0) {
          width /= xres;
          }
          if (yres > 0) {
          height /= yres;
          }
	*/

	RenderedImage decodedImage = decoder.decodeAsRenderedImage();
	Raster raster = decodedImage.getData();

	logger.info(file + " has " + ndirs + " image; width="+width
                    +" height="+height +" xres="+xres+unit
                    +" yres="+yres+unit+" bpp="+bpp
                    +" photometric="+photometric+" rows="+rows
                    +" nbands="+raster.getNumBands());

        return raster;
    }

    public static void encode (String file, RenderedImage image) 
        throws IOException {
        encode (new File (file), image);
    }

    public static void encode (File file, RenderedImage image) 
        throws IOException {
        encode (new FileOutputStream (file), image);
    }

    public static void encode (OutputStream os, RenderedImage image) 
        throws IOException {
        if (image == null) {
            throw new IllegalArgumentException ("Image is null");
        }

        TIFFEncodeParam param = new TIFFEncodeParam ();
        //param.setCompression (TIFFEncodeParam.COMPRESSION_GROUP4);
        ImageEncoder encoder = ImageCodec.createImageEncoder
            ("TIFF", os, param);
        encoder.encode(image);
    }

    public static void encode (String file, Raster raster, 
                               ColorModel model) throws IOException {
        encode (new FileOutputStream (file), raster, model);
    }

    public static void encode (File file, Raster raster, 
                               ColorModel model) throws IOException {
        encode (new FileOutputStream (file), raster, model);
    }

    public static void encode (OutputStream os, Raster raster, 
                               ColorModel model) throws IOException {
        if (raster == null) {
            throw new IllegalArgumentException ("Raster is null");
        }

        TIFFEncodeParam param = new TIFFEncodeParam ();
        ImageEncoder encoder = ImageCodec.createImageEncoder
            ("TIFF", os, param);
        encoder.encode(raster, model);        
    }
}
