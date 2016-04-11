package org.lemming.data;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javolution.util.FastMap;
import javolution.util.FastTable;

import org.lemming.interfaces.Store;

/**
 * @author Ronny Sczech
 *
 */
public class ExtendableTable {
	
	private int nRows = 0;
	private FastMap<String, FastTable<Object>> table = new FastMap<String, FastTable<Object>>();
	
	/**
	 * 
	 */
	public ExtendableTable(){
		addLocalizationMembers();
	}
	
	/**
	 * @param member - member
	 */
	public void addNewMember(String member) {
		table.put(member,new FastTable<Object>());
	}
	
	/**
	 * 
	 */
	public void addLocalizationMembers(){
		//table.put("id",new FastTable<Object>());
		table.put("xpix",new FastTable<Object>());
		table.put("ypix",new FastTable<Object>());
	}
	
	
	public Set<String> columnNames(){
		return table.keySet();
	}
	
	/**
	 * @param row - row
	 */
	public void addRow(Map<String,Object> row){
		for (String key : row.keySet()){
			add(key,row.get(key));
		}
		nRows++;
	}
	
	/**
	 * @param row - row
	 * @return data
	 */
	public Map<String,Object> getRow(int row){
		Map<String,Object> map = new FastMap<String,Object>(); // row map
		for (String key : table.keySet())
			map.put(key, table.get(key).get(row));
		return map;
	}
	
	/**
	 * @param col - colummn
	 * @return column
	 */
	public List<Object> getColumn(String col){
		if(table.keySet().contains(col))
			return table.get(col);
		System.err.println("unknown column");
		return null;
	}
	
	/**
	 * @param col - colummn
	 * @return column
	 */
	public Object getData(String col, int row){
		if(table.keySet().contains(col) && row < nRows)
			return table.get(col).get(row);
		System.err.println("unknown column or row");
		return null;
	}
	
	/**
	 * @param member - member 
	 * @param o - object
	 */
	public void add(String member, Object o){
		if(table.keySet().contains(member)){
			FastTable<Object> t = table.get(member);
			t.add(o);
			return;
		}
		System.err.println("unknown column");
	}
	
	
	/**
	 * @return number of rows
	 */
	public int getNumberOfRows() {
		return nRows;
	}
	
	
	/**
	 * This method provides a bridge between the Workspace abstraction and the Store abstraction. 
	 * 
	 * It creates a mutable view on the workspace which allows a module working with Stores to have read/write access to the Workspace in a 
	 * first-in-first-out order using the methods provided by the Store interface. 
	 * 
	 * The put method adds the data to the end of the table, the get keeps track of the last row read. 
	 * The get method is NON-BLOCKING: if the table is empty, or you read all rows, it returns 'null'.
	 *  
	 * @return a class implementing the Store interface.
	 */
	public Store<Map<String,Object>> getFIFO() {
		return new Store<Map<String,Object>> () {
			int lastRow = 0;
						
			@Override
			public boolean isEmpty() {
				return lastRow >= nRows;
			}
			
			@Override
			public Map<String,Object> get() {
				if (isEmpty()) {
					Map<String, Object> row = getRow(lastRow-1);
					for (String key : row.keySet())
						row.put(key, new LastElement(true));
					return row;
				}
				
				return getRow(lastRow++);
			}
			
			@Override
			public void put(Map<String, Object> el) {
				addRow(el);
				nRows++;			
			}			

		};
	}
}
