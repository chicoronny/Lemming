package org.lemming.processors;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

import org.lemming.data.XYFLocalization;
import org.lemming.interfaces.Frame;
import org.lemming.interfaces.Localization;
import org.lemming.processors.SingleInputSingleOutput;

/**
 * @author Ronny Sczech
 *
 * @param <T> - data type
 * @param <F> - frame type
 */
public class PeakFinder<T extends RealType<T>, F extends Frame<T>> extends SingleInputSingleOutput<F,Localization> {

	/** The intensity of a pixel must be greater than {@code threshold} to be considered a local maximum */
	private double threshold;
	protected boolean hasMoreOutputs;
	private int size;
	private long start;

	/**
	 * @param threshold - threshold for subtracting background
	 */
	public PeakFinder(double threshold, int size) {
		start=System.currentTimeMillis();
		this.setThreshold(threshold);
		this.size = size;
		hasMoreOutputs = true;
		setNumThreads();
 	}
	
	@Override
	public void process(F frame) {
		if (frame==null) return;
		process1(frame);
		if (frame.isLast()){ // make the poison pill
			long end = System.currentTimeMillis();
			System.out.println("Last frame finished:"+frame.getFrameNumber()+" in "+(end-start)+" ms");
			XYFLocalization lastloc = new XYFLocalization(frame.getFrameNumber(), 0, 0);
			lastloc.setLast(true);
			output.put(lastloc);
			hasMoreOutputs = false;
			stop();
			return;
		}
		if (frame.getFrameNumber() % 100 == 0)
			System.out.println("Frames finished:"+frame.getFrameNumber());
	}
	
	private void process1(Frame<T> frame) {
		
		Interval interval = Intervals.expand( frame.getPixels(), -size );
		
		RandomAccessibleInterval<T> source = Views.interval( frame.getPixels(), interval );
		
		final Cursor< T > center = Views.iterable( source ).cursor();

		final RectangleShape shape = new RectangleShape( size, true );

		for ( final Neighborhood< T > localNeighborhood : shape.neighborhoods( source ) )
		{
		    // what is the value that we investigate?
		    // (the center cursor runs over the image in the same iteration order as neighborhood)
		    final T centerValue = center.next();
		    
		    if (centerValue.getRealDouble() < getThreshold()) 
		    	continue;
		
		    // keep this boolean true as long as no other value in the local neighborhood
		    // is larger or equal
		    boolean isMaximum = true;
		
		    // check if all pixels in the local neighborhood that are smaller
		    for ( final T value : localNeighborhood )
		    {
		        // test if the center is smaller than the current pixel value
		        if ( centerValue.compareTo( value ) <= 0 )
		        {
		            isMaximum = false;
		            break;
		        }
		    }
		    
		    if (isMaximum)
		    	output.put(new XYFLocalization(frame.getFrameNumber(), center.getIntPosition(0), center.getIntPosition(1)));
		}
		
		//for now just print the results to the console
		//List<Integer> localMax = new ArrayList<Integer>();
				
		//System.out.println(Long.toString(frameNo)+":"+localMax.toString());
	}
		
	/**
	 * @return Threshold
	 */
	public double getThreshold() {
		return threshold;
	}

	/**
	 * @param threshold - threshold for subtracting background
	 */
	public void setThreshold(double threshold) {
		this.threshold = threshold;
	}

	@Override
	public boolean hasMoreOutputs() {
		return hasMoreOutputs;
	}
	
}
