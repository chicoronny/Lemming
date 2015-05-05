package org.lemming.processors;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.imglib2.algorithm.MultiThreaded;

import org.lemming.interfaces.Processor;
import org.lemming.interfaces.Store;
import org.lemming.outputs.NullStoreWarning;

/**
 * @author Ronny Sczech
 *
 * @param <T1> - data type
 * @param <T2> - data type
 */
public abstract class SingleInputSingleOutput<T1,T2> implements Runnable, Processor<T1, T2>, MultiThreaded {

	protected Store<T1> input;
	protected Store<T2> output;
	private volatile boolean running;
	private int numTasks;
	private final ExecutorService service;
	
	/**
	 * 
	 */
	public SingleInputSingleOutput(){
		this.running = true;
		this.numTasks = 1;
		service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()+1);
		//slicesForThread = new Hashtable<Thread, int[]>(numThreads-1);
	}

	@Override
	public void run() {
		
		if (input==null || output==null)
			throw new NullStoreWarning(this.getClass().getName()); 
		
		final ArrayList< Future< Void > > futures = new ArrayList< Future< Void > >();
		
		for ( int taskNum = 0; taskNum < numTasks; ++taskNum ){

			final Callable< Void > r = new Callable< Void >(){
				
				@Override
				public Void call() {
					while (running) {
							if (Thread.currentThread().isInterrupted()) break;
							T1 data = nextInput();
							process(data);
					}
					return null;
				}
				
			};
			futures.add( service.submit( r ) );
		}
		
		for ( final Future< Void > f : futures )
		{
			try
			{
				f.get();
			}
			catch ( final InterruptedException | ExecutionException  e )
			{
				System.err.println(e.getMessage());
			}
		}
				
	}
	
	/**
	 * Method to be overwritten by childs of this class.
	 * @param element - element
	 */
	public abstract void process(T1 element);
	
	T1 nextInput() {
		return input.get();
	}
	
	/**
	 * 
	 */
	public void stop(){
		running = false;
		service.shutdownNow();
	
		/*for (int ithread = 0; ithread < threads.length; ithread++) {
			threads[ithread].interrupt();
		}/*/
	}

	@Override
	public void setInput(Store<T1> s) {
		input = s;
	}

	@Override
	public void setOutput(Store<T2> s) {
		output = s;
	}
	
	@Override
	public int getNumThreads() {
		return (int) Math.floor(numTasks/50);
	}

	@Override
	public void setNumThreads() {
		this.numTasks=Runtime.getRuntime().availableProcessors();
	}

	@Override
	public void setNumThreads(int numThreads) {
		this.numTasks=numThreads*50;		
	}

}
