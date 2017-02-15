package org.lemming.modules;

import ij.ImagePlus;
import ij.ImageStack;
import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.real.FloatType;

import java.util.List;

import org.lemming.interfaces.Element;
import org.lemming.pipeline.ImgLib2Frame;
import org.lemming.pipeline.SingleRunModule;

/**
 * loading images onto the queue
 * 
 * @author Ronny Sczech
 *
 * @param <T> - data type
 */
public class ImageLoader extends SingleRunModule{
	
	private int curSlice = 0;
	private final ImageStack img;
	private final int stackSize;
	private final double pixelDepth;
	private final Double offset;
	private final Double em_gain;
	private final Double conversion;
	private final ImgFactory< FloatType > imgFactory;

	public ImageLoader(ImagePlus loc_im, List<Double> cameraSettings) {
		this.img = loc_im.getStack();
		stackSize = loc_im.getNSlices()*loc_im.getNFrames()*loc_im.getNChannels();
		pixelDepth = loc_im.getCalibration().pixelDepth == 0 ? cameraSettings.get(3) : loc_im.getCalibration().pixelDepth;
		offset = cameraSettings.get(0);
		em_gain = cameraSettings.get(1);
		conversion = cameraSettings.get(2);
		Thread.currentThread().setName("ImageLoader");
		imgFactory = new ArrayImgFactory< FloatType >();
	}
	
	@Override
	public void beforeRun() {
		start = System.currentTimeMillis();
		iterator = outputs.keySet().iterator().next();
	}

	@Override
	public Element processData(Element data) {	
		Img<FloatType> theImage = imgFactory.create(new long[]{img.getWidth(), img.getHeight()}, new FloatType());
		int index=0;
		Object ip = img.getProcessor(++curSlice).getPixels();
		String className = ip.getClass().getName();
		final Cursor<FloatType> it = theImage.cursor();
		if (className.contains("[S")) {
			short[] ipN = (short[]) ip;
			while(it.hasNext()){
				it.fwd();
				final double adu = Math.max((ipN[index++]-offset), 0);
				final double im2phot = adu*conversion/em_gain;
				it.get().setReal(im2phot);
			}
		} else if (className.contains("[F")) {
			float[] ipN = (float[]) ip;
			while(it.hasNext()){
				it.fwd();
				final double adu = Math.max((ipN[index++]-offset), 0);
				final double im2phot = adu*conversion/em_gain;
				it.get().setReal(im2phot);
			}
		} else if (className.contains("[B")) {
			boolean[] ipN = (boolean[]) ip;
			while(it.hasNext()){
				it.fwd();
				if (ipN[index++]) it.get().setOne();
				else it.get().setZero();
			}
		} else if (className.contains("[I")) {
			int[] ipN = (int[]) ip;
			while(it.hasNext()){
				it.fwd();
				final double adu = Math.max((ipN[index++]-offset), 0);
				final double im2phot = adu*conversion/em_gain;
				it.get().setReal(im2phot);
			}
		} else if (className.contains("[D")) {
			double[] ipN = (double[]) ip;
			while(it.hasNext()){
				it.fwd();
				final double adu = Math.max((ipN[index++]-offset), 0);
				final double im2phot = adu*conversion/em_gain;
				it.get().setReal(im2phot);
			}
		}
	
		ImgLib2Frame<FloatType> frame = new ImgLib2Frame<>(curSlice, img.getWidth(), img.getHeight(), pixelDepth, theImage);

		if (curSlice >= stackSize){
			frame.setLast(true);
			cancel(); 
			return frame; 
		}
		return frame;
	}
	
	@Override
	public void afterRun(){
		System.out.println("Loading of " + stackSize +" done in " + (System.currentTimeMillis()-start) + "ms.");
	}
	
	@Override
	public boolean check() {
		return outputs.size()>=1;
	}
}
