package org.lemming.interfaces;

import java.util.Map;
import java.util.concurrent.ExecutorService;


/**
 * Interface for all modules, inspired by the org.scijava.module interface
 * 
 * @author Ronny Sczech
 */

public interface ModuleInterface extends Runnable{
	
	public void cancel();
	
	public Object getInput(Integer key);
	
	public Map<Integer, Element> getInputs();
	
	public Object getOutput(Integer key);
	
	public Map<Integer, Element> getOutputs();
	
	public void setInput(Integer key, Store store);
	
	public void setInputs(Map<Integer, Store> storeMap);
	
	public void setOutput(Integer key, Store store);
	
	public void setOutputs(Map<Integer, Store> storeMap);
	
	public void setOutput(Store s);

	public void setInput(Store s);
	
	public boolean check();

	public void setService(ExecutorService service);	
}
