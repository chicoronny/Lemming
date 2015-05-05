package org.lemming.processors;

import java.util.ArrayList;

import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Sampler;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

/**
* Provides findLocalExtrema(RandomAccessibleInterval, LocalNeighborhoodCheck, int)
* to find pixels that are extrema in their local neighborhood.
*
* @author Tobias Pietzsch
* @author Ronny Sczech
*/


public class LocalExtrema {
	/**
	* A local extremum check.
	*
	* @param <P>
	* A representation of the extremum. For example, this could be
	* just a {@link Point} describing the location of the extremum.
	* It could contain additional information such as the value at
	* the extremum or an extremum type.
	* @param <T>
	* pixel type.
	*/
	
	public interface LocalNeighborhoodCheck< P, T extends Comparable< T > >
	{
	/**
	* Determine whether a pixel is a local extremum. If so, return a
	* <code>P</code> that represents the maximum. Otherwise return
	* <code>null</code>.
	*
	* @param center
	* an access located on the pixel to test
	* @param neighborhood
	* iterable neighborhood of the pixel, not containing the
	* pixel itself.
	* @param <C> - Center Type
	* @return null if the center not a local extremum, a P if it is.
	*/
	public < C extends Localizable & Sampler< T > > P check( C center, Neighborhood< T > neighborhood );
	}
	
	/**
	* A {@link LocalNeighborhoodCheck} to test whether a pixel is a local
	* maximum. A pixel is considered a maximum if its value is greater than or
	* equal to a specified minimum allowed value, and no pixel in the
	* neighborhood has a greater value. That means that maxima are non-strict.
	* Intensity plateaus may result in multiple maxima.
	*
	* @param <T>
	* pixel type.
	*
	* @author Tobias Pietzsch
	*/
	public static class MaximumCheck< T extends Comparable< T > > implements LocalNeighborhoodCheck< Point, T >
	{
		final T minPeakValue;
		
		/**
		 * @param minPeakValue - minimum PeakValue
		 */
		public MaximumCheck( final T minPeakValue )
		{
			this.minPeakValue = minPeakValue;
		}
		
		@Override
		public < C extends Localizable & Sampler< T > > Point check( final C center, final Neighborhood< T > neighborhood )
		{
			final T c = center.get();
			
			if ( minPeakValue.compareTo( c ) > 0 )
				return null;
			
			for ( final T t : neighborhood )
				if ( t.compareTo( c ) > 0 )
					return null;
			
			return new Point( center );
		}
	}
	
	/**
	* A {@link LocalNeighborhoodCheck} to test whether a pixel is a local
	* minimum. A pixel is considered a minimum if its value is less than or
	* equal to a specified maximum allowed value, and no pixel in the
	* neighborhood has a smaller value. That means that minima are non-strict.
	* Intensity plateaus may result in multiple minima.
	*
	* @param <T>
	* pixel type.
	*
	* @author Tobias Pietzsch
	*/
	public static class MinimumCheck< T extends Comparable< T > > implements LocalNeighborhoodCheck< Point, T >
	{
		final T maxPeakValue;
		
		/**
		 * @param maxPeakValue - maximal PeakValue
		 */
		public MinimumCheck( final T maxPeakValue )
		{
			this.maxPeakValue = maxPeakValue;
		}
		
		@Override
		public < C extends Localizable & Sampler< T > > Point check( final C center, final Neighborhood< T > neighborhood )
		{
			final T c = center.get();
			
			if ( maxPeakValue.compareTo( c ) < 0 )
				return null;
			
			for ( final T t : neighborhood )
				if ( t.compareTo( c ) < 0 )
					return null;
			
			return new Point( center );
		}
	}
	
	/**
	 * Use of the NMS algorithm for peak finding.
	 * This method returns null if the peak is lower than the given threshold or if one of the pixels around the found maximum is higher or equal.
	 * The check is done in an extended kernel by putting the found maximum to the center of a kernel with the same kernel size as the original one.
	 * 
	 * @author Ronny Sczech
	 *
	 * @param <T>
	 * pixel type.
	 */
	public static class MaximumFinder< T extends Comparable< T > & RealType< T > > {
		
		private final double minPeakValue;
		private T maximum;
		
		/**
		 * @param cutoff - minimum PeakValue
		 */
		public MaximumFinder(final double cutoff){
			this.minPeakValue = cutoff;
			maximum = null;
		}

		/**
		 * @param randomAccessible 
		 * randomAccessible for lookup
		 * @param p - Localizing object
		 * @param step - step
		 * @param size - size of kernel
		 * @return maximum
		 */
		public Point check(final RandomAccessibleInterval<T> randomAccessible, final Localizable p, final int step, final int size) {
			
			final IntervalView<T> neighborhood = Views.interval(randomAccessible, createSpan(p, step, size));	
			
			final Cursor<T> cursor = neighborhood.cursor();
			
			T type = cursor.next();
			maximum = type.copy();
			Point maxLocation = new Point(cursor);
			
			while (cursor.hasNext()){
				type = cursor.next();
				if ( type.compareTo( maximum ) > 0 ){
					maximum.set(type);
					maxLocation.setPosition( cursor );
				}
			}
			if ( minPeakValue > maximum.getRealDouble() ) return null;
			
			// check with the kernel size for outer pixels to be of higher value
			
			final int n = neighborhood.numDimensions();
			final long[] min = new long[ n ];
			final long[] max = new long[ n ];

			boolean centered = true,
					failed = false;
			
			for ( int d = 0; d < n; ++d ){ 
				final long k = (neighborhood.dimension(d)-1)/2;
				if (maxLocation.getLongPosition(d) - (neighborhood.max(d) - k) == 0){ // whole kernel
					min[d] = maxLocation.getLongPosition(d) - k;
					max[d] = maxLocation.getLongPosition(d) + k;
				} else {
					centered = false;
				}
			}
			
			if (centered) return maxLocation;
			
			final IntervalView<T> outer = Views.interval( randomAccessible, new FinalInterval( min, max ));
			final Cursor<T> outerCursor = outer.cursor();
			T vvalue = outerCursor.next();
			
			while (outerCursor.hasNext()){
				vvalue = outerCursor.next();
				if ( vvalue.compareTo( maximum ) >= 0 ){
					failed = true;
					break;
				}
			}
			
			if (failed)
				return null;
			
			return maxLocation;
		}
		
		
	}
	
	/**
	 * @param p - Localizable
	 * @param step - step 
	 * @param span - span
	 * @return neighborhood
	 */
	public static Interval createSpan( final Localizable p , final int step, final int span )
	{
		int n = p.numDimensions();
		
		final long[] min = new long[ n ];
		final long[] max = new long[ n ];
		final long[] position = new long[ n ];
		p.localize( position ); 
		for ( int d = 0; d < n; ++d )
		{
			min[ d ] = position[d] * step - span;
			max[ d ] = position[d] * step + span;
		}
		return new FinalInterval( min, max );
	}
	
	/**
	 * @param img - Image
	 * @param localNeighborhoodCheck - Neighborhood check
	 * @param size - kernel size
	 * @param <P> - Neighborhood Type
	 * @param <T> - Pixel Type
	 * @return local extreme values
	 */
	public static < P, T extends Comparable< T > > ArrayList< P > findLocalExtrema( final RandomAccessibleInterval< T > img, final LocalNeighborhoodCheck< P, T > localNeighborhoodCheck, int size)
	{
		
		final RectangleShape shape = new RectangleShape( size, false );

		final ArrayList< P > extrema = new ArrayList< P >(1);
		
		final Cursor< T > center = Views.flatIterable( img ).cursor();
		
		for ( final Neighborhood< T > neighborhood : shape.neighborhoods( img ) ){
			center.fwd();
			final P p = localNeighborhoodCheck.check( center, neighborhood );
			if ( p != null )
				extrema.add( p );
		}		
		
		return extrema ;		
	}
}
