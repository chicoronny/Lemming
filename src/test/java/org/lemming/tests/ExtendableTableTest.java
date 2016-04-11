package org.lemming.tests;

import java.util.Map;
import java.util.Set;

import javolution.util.FastMap;

import org.junit.Before;
import org.junit.Test;
import org.lemming.data.ExtendableTable;

@SuppressWarnings("javadoc")
public class ExtendableTableTest {

	@Before
	public void setUp() throws Exception {
	}

	@Test
	public void testExtendableTable() {
		int N = (int) 1e7;
		ExtendableTable h = new ExtendableTable();
		h.addNewMember("z");
		h.addNewMember("frame");
		h.addNewMember("roi");
		
		long t0 = System.currentTimeMillis();
		long t00 = t0;
		long[] T = new long[N];
		
		long sum = 0;
		long max = 0;
		long id = 0;			
		
		
		for (int i=0;i<N;i++){
			Set<String> gi = h.columnNames();
			Map<String,Object> em = new FastMap<>();
			if (gi.contains("xpix")) em.put("xpix", i);
			if (gi.contains("ypix")) em.put("ypix", -i);
			if (gi.contains("z")) em.put("z", i);
			if (gi.contains("frame")) em.put("frame", i+1);
			if (gi.contains("roi")) em.put("roi", new double[] {1,2,3,4});			
			h.addRow(em);
			
			long ct = System.currentTimeMillis();
			long dt = ct - t0;
			
			t0=ct;
			
			sum+=dt;
			
			if (max < dt) {
				max=dt;
				id=i;
			}
			
			T[i] = dt;
		}
		
		System.out.println("Total time is "+(System.currentTimeMillis()-t00));
		System.out.println("loop is "+ N);
		System.out.println("Average time is "+sum/N);
		System.out.println("Max time is "+max);		
		System.out.println("row is "+id+":"+T[(int) id]);	
		System.gc();
	}
	


}
