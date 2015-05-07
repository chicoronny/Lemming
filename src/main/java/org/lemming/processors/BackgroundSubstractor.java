package org.lemming.processors;

import ij.process.FloatProcessor;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

import org.lemming.data.ImgLib2Frame;
import org.lemming.interfaces.Frame;


public class BackgroundSubstractor<T extends RealType<T> & NativeType<T>, F extends Frame<T>> extends SingleInputSingleOutput<F, F> {

	private boolean hasMoreOutputs=true;
	private long start;
	private boolean invert=false;
	private boolean doPresmooth=false;
	private double radius=1;
	
	public BackgroundSubstractor(double radius, boolean doPresmooth, boolean invert){
		start=System.currentTimeMillis();
		hasMoreOutputs = true;
		new ArrayImgFactory<T>();
		this.radius = radius;
		this.doPresmooth = doPresmooth;
		this.invert = invert;
		setNumThreads();
	}
	
	@Override
	public boolean hasMoreOutputs() {
		return hasMoreOutputs;
	}

	@Override
	public void process(F frame) {
		if (frame==null) return;
		process1(frame);
		if (frame.isLast()){
			long end = System.currentTimeMillis();
			System.out.println("Last frame finished:"+frame.getFrameNumber()+" in "+(end-start)+" ms");
			frame.setLast(true);
			output.put(frame);
			hasMoreOutputs = false;
			stop();
			return;
		}
		if (frame.getFrameNumber() % 500 == 0)
			System.out.println("Frames finished:"+frame.getFrameNumber());
	}
	
	private void invert(float[] pixels){
		for (int i=0; i<pixels.length; i++)
            pixels[i] = -pixels[i];
	}
	
	private FloatProcessor wrap(RandomAccessibleInterval<T> source){
		FloatProcessor fp = new FloatProcessor((int)source.dimension(0),(int)source.dimension(1));
		float[] pixels = (float[])fp.getPixels();
		Cursor<T> cursor = Views.flatIterable(source).cursor();
		int pp = 0;
		while(cursor.hasNext())
			pixels[pp++]=cursor.next().getRealFloat();

		return fp;
	}

	@SuppressWarnings("unchecked")
	private void process1(F frame) {
		
		RandomAccessibleInterval<T> interval = frame.getPixels();
		RollingBall ball = new RollingBall(radius);
        boolean shrink = ball.shrinkFactor >1;
        String typename = Views.iterable(interval).firstElement().getClass().getSimpleName();
        T type = Views.iterable(interval).firstElement().createVariable();
        FloatProcessor fp = wrap(interval);
        float[] pixels = (float[])fp.getPixels();
        
        if (typename.contains("FloatType"))
        	fp.snapshot();

        if (invert)
            invert(pixels);
        if (doPresmooth)
            filter3x3(fp);
        if (Thread.currentThread().isInterrupted()) return;
        FloatProcessor smallImage = shrink ? shrinkImage(fp, ball.shrinkFactor) : fp;
        if (Thread.currentThread().isInterrupted()) return;
        rollBall(ball, smallImage);
        if (Thread.currentThread().isInterrupted()) return;
        if (shrink)
        	enlargeImage(smallImage,fp, ball.shrinkFactor); //interval will be overwritten !
        if (Thread.currentThread().isInterrupted()) return;

        if (invert)
            invert(pixels);
        
        RandomAccessibleInterval<T> out = unwrap(interval, fp,type);
        output.put((F) new ImgLib2Frame<T>(frame.getFrameNumber(),(int) out.dimension(0), (int)out.dimension(1), out));
	}
	
	
    private RandomAccessibleInterval<T> unwrap(RandomAccessibleInterval<T> interval, FloatProcessor fp, T type) {
    	float[] bgPixels = (float[])fp.getPixels();
    	String typename = type.getClass().getSimpleName();
    	Img<T> out = new ArrayImgFactory<T>().create(new long[]{fp.getWidth(), fp.getHeight()}, type);
    	RandomAccess<T> ra = out.randomAccess();
    	Cursor<T> cursor = Views.flatIterable(interval).cursor();
    	int p=0;
    	if (typename.contains("FloatType")){
    		float[] snapshotPixels = (float[])fp.getSnapshotPixels(); //original data in the snapshot
    		while(cursor.hasNext()){
    			cursor.fwd();
    			ra.setPosition(cursor);
    			ra.get().setReal(snapshotPixels[p]-bgPixels[p]);
    			p++;
    		}
    	} else if (typename.contains("UnsignedShortType")){
    		float offset = invert ? 65535.0f : 0f;//includes 0.5 for rounding when converting float to short
            while(cursor.hasNext()){
    			cursor.fwd();
    			float value = cursor.get().getRealFloat() - bgPixels[p] + offset;
                if (value<0f) value = 0f;
                if (value>65535f) value = 65535f;
    			ra.setPosition(cursor);
    			ra.get().setReal(value);
    			p++;
    		}
    	} else if (typename.contains("UnsignedByteType")){
    		float offset = invert ? 255.0f : 0f;//includes 0.5 for rounding when converting float to short
            while(cursor.hasNext()){
    			cursor.fwd();
    			float value = cursor.get().getRealFloat() - bgPixels[p] + offset;
                if (value<0f) value = 0f;
                if (value>255f) value = 255f;
    			ra.setPosition(cursor);
    			ra.get().setReal(value);
    			p++;
    		}
    	}
    	
		return out;
	}

	/** Creates a lower resolution image for ball-rolling. */
    FloatProcessor shrinkImage(FloatProcessor ip, int shrinkFactor) {
        int width = ip.getWidth();
        int height = ip.getHeight();
        float[] pixels = (float[])ip.getPixels();
        int sWidth = (width+shrinkFactor-1)/shrinkFactor;
        int sHeight = (height+shrinkFactor-1)/shrinkFactor;
        FloatProcessor smallImage = new FloatProcessor(sWidth, sHeight);
        float[] sPixels = (float[])smallImage.getPixels();
        float min, thispixel;
        
        for (int ySmall=0; ySmall<sHeight; ySmall++) {
            for (int xSmall=0; xSmall<sWidth; xSmall++) {
                min = Float.MAX_VALUE;
                for (int j=0, y=shrinkFactor*ySmall; j<shrinkFactor&&y<height; j++, y++) {
                    for (int k=0, x=shrinkFactor*xSmall; k<shrinkFactor&&x<width; k++, x++) {
                        thispixel = pixels[x+y*width];
                        if (thispixel<min)
                            min = thispixel;
                    }
                }
                sPixels[xSmall+ySmall*sWidth] = min; // each point in small image is minimum of its neighborhood
            }
        }
        return smallImage;
    }

    /** 'Rolls' a filtering object over a (shrunken) image in order to find the
        image's smooth continuous background.  For the purpose of explaining this
        algorithm, imagine that the 2D grayscale image has a third (height) dimension
        defined by the intensity value at every point in the image.  The center of
        the filtering object, a patch from the top of a sphere having radius BallRadius,
        is moved along each scan line of the image so that the patch is tangent to the
        image at one or more points with every other point on the patch below the
        corresponding (x,y) point of the image.  Any point either on or below the patch
        during this process is considered part of the background.  Shrinking the image
        before running this procedure is advised for large ball radii because the
        processing time increases with ball radius^2.
    */
    void rollBall(RollingBall ball, FloatProcessor fp) {
        float[] pixels = (float[])fp.getPixels();   //the input pixels
        int width = fp.getWidth();
        int height = fp.getHeight();
        float[] zBall = ball.data;
        int ballWidth = ball.width;
        int radius = ballWidth/2;
        float[] cache = new float[width*ballWidth]; //temporarily stores the pixels we work on

        for (int y=-radius; y<height+radius; y++) { //for all positions of the ball center:
            int nextLineToWriteInCache = (y+radius)%ballWidth;
            int nextLineToRead = y + radius;        //line of the input not touched yet
            if (nextLineToRead<height) {
                System.arraycopy(pixels, nextLineToRead*width, cache, nextLineToWriteInCache*width, width);
                for (int x=0, p=nextLineToRead*width; x<width; x++,p++)
                    pixels[p] = -Float.MAX_VALUE;   //unprocessed pixels start at minus infinity
            }
            int y0 = y-radius;                      //the first line to see whether the ball touches
            if (y0 < 0) y0 = 0;
            int yBall0 = y0-y+radius;               //y coordinate in the ball corresponding to y0
            int yend = y+radius;                    //the last line to see whether the ball touches
            if (yend>=height) yend = height-1;
            for (int x=-radius; x<width+radius; x++) {
                float z = Float.MAX_VALUE;          //the height of the ball (ball is in position x,y)
                int x0 = x-radius;
                if (x0 < 0) x0 = 0;
                int xBall0 = x0-x+radius;
                int xend = x+radius;
                if (xend>=width) xend = width-1;
                for (int yp=y0, yBall=yBall0; yp<=yend; yp++,yBall++) { //for all points inside the ball
                    int cachePointer = (yp%ballWidth)*width+x0;
                    for (int xp=x0, bp=xBall0+yBall*ballWidth; xp<=xend; xp++, cachePointer++, bp++) {
                        float zReduced = cache[cachePointer] - zBall[bp];
                        if (z > zReduced)           //does this point imply a greater height?
                            z = zReduced;
                    }
                }
                for (int yp=y0, yBall=yBall0; yp<=yend; yp++,yBall++) //raise pixels to ball surface
                    for (int xp=x0, p=xp+yp*width, bp=xBall0+yBall*ballWidth; xp<=xend; xp++, p++, bp++) {
                        float zMin = z + zBall[bp];
                        if (pixels[p] < zMin)
                            pixels[p] = zMin;
                    }
            }
        }        
    }
    
    /** Uses bilinear interpolation to find the points in the full-scale background
        given the points from the shrunken image background. (At the edges, it is
        actually extrapolation.)
    */                                 
    void enlargeImage(FloatProcessor smallImage, FloatProcessor fp, int shrinkFactor) {
        int width = fp.getWidth();
        int height = fp.getHeight();
        int smallWidth = smallImage.getWidth();
        int smallHeight = smallImage.getHeight();
        float[] pixels = (float[])fp.getPixels();
        float[] sPixels = (float[])smallImage.getPixels();
        int[] xSmallIndices = new int[width];         //index of first point in smallImage
        float[] xWeights = new float[width];        //weight of this point
        makeInterpolationArrays(xSmallIndices, xWeights, width, smallWidth, shrinkFactor);
        int[] ySmallIndices = new int[height];
        float[] yWeights = new float[height];
        makeInterpolationArrays(ySmallIndices, yWeights, height, smallHeight, shrinkFactor);
        float[] line0 = new float[width];
        float[] line1 = new float[width];
        for (int x=0; x<width; x++)                 //x-interpolation of the first smallImage line
            line1[x] = sPixels[xSmallIndices[x]] * xWeights[x] +
                    sPixels[xSmallIndices[x]+1] * (1f - xWeights[x]);
        int ySmallLine0 = -1;                       //line0 corresponds to this y of smallImage
        for (int y=0; y<height; y++) {
            if (ySmallLine0 < ySmallIndices[y]) {
                float[] swap = line0;               //previous line1 -> line0
                line0 = line1;
                line1 = swap;                       //keep the other array for filling with new data
                ySmallLine0++;
                int sYPointer = (ySmallIndices[y]+1)*smallWidth; //points to line0 + 1 in smallImage
                for (int x=0; x<width; x++)         //x-interpolation of the new smallImage line -> line1
                    line1[x] = sPixels[sYPointer+xSmallIndices[x]] * xWeights[x] +
                            sPixels[sYPointer+xSmallIndices[x]+1] * (1f - xWeights[x]);
            }
            float weight = yWeights[y];
            for (int x=0, p=y*width; x<width; x++,p++)
                pixels[p] = line0[x]*weight + line1[x]*(1f - weight);
        }
    }

    /** Create arrays of indices and weigths for interpolation.
     <pre>
     Example for shrinkFactor = 4:
        small image pixel number         |       0       |       1       |       2       | ...
        full image pixel number          | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |10 |11 | ...
        smallIndex for interpolation(0)  | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 1 | 1 | 1 | 2 | 2 | ...
     (0) Note: This is smallIndex for the left pixel; for the right pixel used for interpolation
               it is higher by one
     </pre>
     */
    void makeInterpolationArrays(int[] smallIndices, float[] weights, int length, int smallLength, int shrinkFactor) {
        for (int i=0; i<length; i++) {
            int smallIndex = (i - shrinkFactor/2)/shrinkFactor;
            if (smallIndex >= smallLength-1) smallIndex = smallLength - 2;
            smallIndices[i] = smallIndex;
            float distance = (i + 0.5f)/shrinkFactor - (smallIndex + 0.5f); //distance of pixel centers (in smallImage pixels)
            weights[i] = 1f - distance;
        }
    }

    //   C O M M O N   S E C T I O N   F O R   B O T H   A L G O R I T H M S

    /** Replace the pixels by the mean or maximum in a 3x3 neighborhood.
     *  No snapshot is required (less memory needed than e.g., fp.smooth()).
     *  When used as maximum filter, it returns the average change of the
     *  pixel value by this operation
     */
    double filter3x3(FloatProcessor fp) {
        int width = fp.getWidth();
        int height = fp.getHeight();
        double shiftBy = 0;
        float[] pixels = (float[])fp.getPixels();
        for (int y=0; y<height; y++)
            shiftBy += filter3(pixels, width, y*width, 1);
        for (int x=0; x<width; x++)
            shiftBy += filter3(pixels, height, x, width);
        return shiftBy/width/height;
    }

    /** Filter a line: maximum or average of 3-pixel neighborhood */
    double filter3(float[] pixels, int length, int pixel0, int inc) {
        double shiftBy = 0;
        float v3 = pixels[pixel0];  //will be pixel[i+1]
        float v2 = v3;              //will be pixel[i]
        float v1;                   //will be pixel[i-1]
        for (int i=0, p=pixel0; i<length; i++,p+=inc) {
            v1 = v2;
            v2 = v3;
            if (i<length-1) v3 = pixels[p+inc];
            pixels[p] = (v1+v2+v3)*0.33333333f;
        }
        return shiftBy;
    }
}
	
	
//  C L A S S   R O L L I N G B A L L

/** A rolling ball (or actually a square part thereof)
 *  Here it is also determined whether to shrink the image
 */
class RollingBall {

    float[] data;
    int width;
    int shrinkFactor;
    
    RollingBall(double radius) {
        int arcTrimPer;
        if (radius<=10) {
            shrinkFactor = 1;
            arcTrimPer = 24; // trim 24% in x and y
        } else if (radius<=30) {
            shrinkFactor = 2;
            arcTrimPer = 24; // trim 24% in x and y
        } else if (radius<=100) {
            shrinkFactor = 4;
            arcTrimPer = 32; // trim 32% in x and y
        } else {
            shrinkFactor = 8;
            arcTrimPer = 40; // trim 40% in x and y
        }
        buildRollingBall(radius, arcTrimPer);
    }
    
    /** Computes the location of each point on the rolling ball patch relative to the 
    center of the sphere containing it.  The patch is located in the top half 
    of this sphere.  The vertical axis of the sphere passes through the center of 
    the patch.  The projection of the patch in the xy-plane below is a square.
    */
    void buildRollingBall(double ballradius, int arcTrimPer) {
        double rsquare;     // rolling ball radius squared
        int xtrim;          // # of pixels trimmed off each end of ball to make patch
        int xval, yval;     // x,y-values on patch relative to center of rolling ball
        double smallballradius; // radius of rolling ball (downscaled in x,y and z when image is shrunk)
        int halfWidth;      // distance in x or y from center of patch to any edge (patch "radius")
        
        smallballradius = ballradius/shrinkFactor;
        if (smallballradius<1)
            smallballradius = 1;
        rsquare = smallballradius*smallballradius;
        xtrim = (int)(arcTrimPer*smallballradius)/100; // only use a patch of the rolling ball
        halfWidth = (int)Math.round(smallballradius - xtrim);
        width = 2*halfWidth+1;
        data = new float[width*width];

        for (int y=0, p=0; y<width; y++)
            for (int x=0; x<width; x++, p++) {
                xval = x - halfWidth;
                yval = y - halfWidth;
                double temp = rsquare - xval*xval - yval*yval;
                data[p] = temp>0. ? (float)(Math.sqrt(temp)) : 0f;
                //-Float.MAX_VALUE might be better than 0f, but gives different results than earlier versions
            }
    }

}

