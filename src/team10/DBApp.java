package team10;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.Set;

public class DBApp {
	public void init() {

	}

	public void createTable(String strTableName, String strClusteringKeyColumn,
			Hashtable<String, String> htblColNameType) throws DBAppException {
		if (strTableName == null || strClusteringKeyColumn == null || htblColNameType == null) {
			throw new DBAppException();
		}
		if (!CreationUtilities.checkMeta()) {
			try {
				CreationUtilities.createMeta();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (CreationUtilities.checkValidName(strTableName) && CreationUtilities.checkValidKeys(htblColNameType)) {
			try {
				CreationUtilities.addMetaData(strTableName, strClusteringKeyColumn, htblColNameType);
			} catch (IOException e) {
				e.printStackTrace();
			}
			CreationUtilities.addDirectory(strTableName, "data");
		} else {
			throw new DBAppException();
		}
		System.out.println("Table Created!");
	}

	public void insertIntoTable(String strTableName, Hashtable<String, Object> htblColNameValue) throws DBAppException {
		String line = null;
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader("data/metadata.csv"));
			line = br.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}

		ArrayList<String[]> data = new ArrayList<>();
		String primaryKey = null;
		ArrayList<String> indexedColumns = new ArrayList<>();
		Hashtable<String, String> ColNameType = new Hashtable<>();

		while (line != null) {
			String[] content = line.split(",");

			if (content[0].equals(strTableName)) {
				data.add(content);
				ColNameType.put(content[1], content[2]);
				if ((content[3].toLowerCase()).equals("true"))
					primaryKey = content[1];
				if (content[4].toLowerCase().equals("true"))
					indexedColumns.add(content[1]);
			}
			try {
				line = br.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		try {
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (data.isEmpty())
			throw new DBAppException("404 Table Not Found !");

		if (htblColNameValue.get(primaryKey).equals(null))
			throw new DBAppException("Primary Key Can NOT be null");

		if (!InsertionUtilities.isValidTuple(ColNameType, htblColNameValue))
			throw new DBAppException(
					"The tuple you're trying to insert into table " + strTableName + " is not a valid tuple!");

		int[] positionToInsertAt;
		if (indexedColumns.contains(primaryKey)) {
			positionToInsertAt = InsertionUtilities.searchForInsertionPositionIndexed(strTableName, primaryKey,
					htblColNameValue);
		} else {
			positionToInsertAt = InsertionUtilities.searchForInsertionPosition(strTableName, primaryKey,
					htblColNameValue);
		}
		// for some reason, Maq's insertTuple modifies the positionToInsertAt.
		// Therefore, this local array is needed
		int[] tempPositionToInsertAt = { positionToInsertAt[0], positionToInsertAt[1] };

		try {
			InsertionUtilities.insertTuple(strTableName, positionToInsertAt, htblColNameValue, true);
		} catch (IOException e) {
			e.printStackTrace();
		}

		ArrayList<Integer> changedPagesAfterDenseIndexUpdate = new ArrayList<Integer>();

		for (int i = 0; i < indexedColumns.size(); i++) {
			if (!indexedColumns.get(i).equals(primaryKey)) {
				changedPagesAfterDenseIndexUpdate = InsertionUtilities.updateDenseIndexAfterInsertion(strTableName,
						indexedColumns.get(i), tempPositionToInsertAt[0], tempPositionToInsertAt[1],
						htblColNameValue.get(indexedColumns.get(i)));
				try {
					IndexUtilities.updateBRINIndexOnDense(strTableName, indexedColumns.get(i),
							changedPagesAfterDenseIndexUpdate);
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				try {
					IndexUtilities.updateBRINIndexOnPK(strTableName, primaryKey, positionToInsertAt[0]);
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		}

		System.out.println("Tuple Inserted!");

	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void updateTable(String strTableName, String strKey, Hashtable<String, Object> htblColNameValue)
			throws DBAppException, ParseException, ClassNotFoundException, IOException {

		/////////// PRELIMINARY CHECKS

		// Check if null values in general
		if (strTableName == null || strKey == null || htblColNameValue == null)
			throw new DBAppException("Do not leave stuff null!");

		// Check if the table exists using checkValidName from CreationUtilites
		if (!IndexUtilities.tableDirectoryExists(strTableName))
			throw new DBAppException("Do you know your tables?");

		// Get Columns and Primary key of needed table (with its type)
		ArrayList<Object> neededData = UpdateUtilities.getColumnsAndKey(strTableName);

		Hashtable<String, String> tblNameType = (Hashtable<String, String>) neededData.get(0);
		String PKeyName = (String) neededData.get(1);
		ArrayList<String> indexedColumns = (ArrayList<String>) neededData.get(2);

		// Check if valid tuple
		if (!InsertionUtilities.isValidTuple(tblNameType, htblColNameValue))
			throw new DBAppException("Stick to to the types!");

		/////////////////////////////////

		// Get the Primary Key as a type
		Object keyValue = UpdateUtilities.getTypedPKey(tblNameType.get(PKeyName), strKey);

		int tblPageNum = 1;
		int tupleRowNum = 0;
		Page currentTblPage;
		// Finding Tuple based on whether the primary key is indexed or not
		if (indexedColumns.contains(PKeyName)) {
			// System.out.println("PK BRIN used for finding tuple.");
			// If yes, use the index to find the tuple
			int curBRINPage = 1;
			MainLoop: while (true) {
				try {
					Page curPage = IndexUtilities
							.retrievePage("data/" + strTableName + "/" + PKeyName + "/indices/BRIN", curBRINPage);
					Comparable target = (Comparable) keyValue;
					for (int i = 0; i < curPage.getRows().length; i++) {

						if ((boolean) curPage.getRows()[i].get("isDeleted"))
							continue;

						Comparable currentRowMax = (Comparable) curPage.getRows()[i].get(PKeyName + "Max");
						Comparable currentRowMin = (Comparable) curPage.getRows()[i].get(PKeyName + "Min");

						// Throw an exception if the value isn't within any
						// of the ranges of the BRIN
						if (curPage.getRows()[i] == null || target.compareTo(currentRowMin) < 0)
							throw new NullPointerException();

						// if the target belongs in the range
						if (target.compareTo(currentRowMax) <= 0 && target.compareTo(currentRowMin) >= 0) {
							tblPageNum = (int) curPage.getRows()[i].get("pageNumber");
							break MainLoop;
						}
					}
					curBRINPage++;
				} catch (DBAppException e) {
					System.out.println(e.getMessage());
				} catch (NullPointerException e) {
					throw new DBAppException("No Record with such key value!");
				}
			}
			// After finding page, find the tuple
			currentTblPage = PageManager.deserializePage("data/" + strTableName + "/page_" + tblPageNum + ".ser");
			boolean found = false;
			// Go through page
			for (int i = 0; i < currentTblPage.getRows().length; i++) {
				Hashtable<String, Object> curRow = currentTblPage.getRows()[i];
				// if a matching row is found
				if (curRow.get(PKeyName).equals(keyValue) && !((boolean) curRow.get("isDeleted"))) {
					tupleRowNum = i;
					// System.out.println("Tuple found in page: " + tblPageNum + " in row: " + tupleRowNum);
					found = true;
					break;
				}
			}
			if (!found) {
				throw new DBAppException("No Record with such key value!");
			}

		} else {
			// System.out.println("PK BRIN not used for finding tuples");
			// Otherwise, go through the table linearly
			MainLoop: while (true) {
				try {
					// Load Page
					currentTblPage = PageManager
							.deserializePage("data/" + strTableName + "/page_" + tblPageNum + ".ser");
					// Go through page
					for (int i = 0; i < currentTblPage.getRows().length; i++) {
						Hashtable<String, Object> curRow = currentTblPage.getRows()[i];
						// if a matching row is found
						if (curRow.get(PKeyName).equals(keyValue) && !((boolean) curRow.get("isDeleted"))) {
							tupleRowNum = i;
							// System.out.println("Tuple found in page: " + tblPageNum + " in row: " + tupleRowNum);
							break MainLoop;
						}
					}
					tblPageNum++;
				} catch (Exception e) {
					throw new DBAppException("You are trying to update a non-existing row!");
				}
			}
		}

		// Now we have the page number and the row number, so update
		if (htblColNameValue.containsKey(PKeyName)) {
			// System.out.println("PK is going to be changed");
			// If the primary key is being changed
			// Check if it doesn't violate
			if (UpdateUtilities.checkNotUsed(strTableName, htblColNameValue.get(PKeyName), PKeyName)) {
				// System.out.println("No violation");
				// Get the old tuple
				Hashtable<String, Object> oldTuple = new Hashtable<>();
				// Get its ID
				oldTuple.put(PKeyName, currentTblPage.getRows()[tupleRowNum].get(PKeyName));
				// Create the new tuple
				Hashtable<String, Object> newTuple = currentTblPage.getRows()[tupleRowNum];
				// Do the updates
				for (String key : htblColNameValue.keySet())
					newTuple.put(key, htblColNameValue.get(key));
				newTuple.put("TouchDate", new Date());
				// Delete the old tuple
				deleteFromTable(strTableName, oldTuple);
				// Insert the new tuple
				insertIntoTable(strTableName, newTuple);
			} else {
				throw new DBAppException("Key Already Used");
			}
		} else {
			Hashtable<String, Object> oldValues = new Hashtable<>();
			Hashtable<String, Object> newValues = new Hashtable<>();

			// Non-prime attributes are updated
			for (String key : htblColNameValue.keySet()) {

				// Store the old and new values if it's indexed
				if (indexedColumns.contains(key)) {
					oldValues.put(key, currentTblPage.getRows()[tupleRowNum].get(key));
					newValues.put(key, htblColNameValue.get(key));
				}

				// Update the value in table
				currentTblPage.getRows()[tupleRowNum].put(key, htblColNameValue.get(key));
			}
			currentTblPage.getRows()[tupleRowNum].put("TouchDate", new Date());
			
			// Store the table
			PageManager.serializePage(currentTblPage, "data/" + strTableName + "/" + "page_" + tblPageNum + ".ser");
			// System.out.println("Table page updated and stored");
			
			// Then update their dense indices, remove PKey as it's not changed
			if (indexedColumns.contains(PKeyName))
				indexedColumns.remove(PKeyName);
			// System.out.println("Going to arrange dense now");
			UpdateUtilities.updateDenseIndex(strTableName, newValues, oldValues, tblPageNum, tupleRowNum);
		}

		System.out.println("Update made successfully!");
	}

	public void deleteFromTable(String strTableName, Hashtable<String, Object> htblColNameValue) throws DBAppException {
		if (strTableName == null)
			throw new DBAppException("No table name provided!");

		if (htblColNameValue == null)
			throw new DBAppException("No record provided!");

		if (CreationUtilities.checkValidName(strTableName))
			throw new DBAppException("Invalid table name!");

		String line = null;
		BufferedReader br = null;
		Hashtable<String, String> colNameType = new Hashtable<>();
		LinkedList<String> indexed_columns = new LinkedList<String>();
		String primaryKey = null;

		try {
			br = new BufferedReader(new FileReader("data/metadata.csv"));
			line = br.readLine();

			while (line != null) {
				String[] content = line.split(",");
				if (content[0].equals(strTableName)) {
					colNameType.put(content[1], content[2]);
					if ((content[3].toLowerCase()).equals("true"))
						primaryKey = content[1];
					if (content[4].toLowerCase().equals("true"))
						indexed_columns.add(content[1]);
				}
				line = br.readLine();
			}

			br.close();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		if (!InsertionUtilities.isValidTuple(colNameType, htblColNameValue))
			throw new DBAppException(
					"The tuple you're trying to delete from table " + strTableName + " is not a valid tuple!");

		Set<String> tableKeys = colNameType.keySet();

		boolean allNull = true;
		for (String tableKey : tableKeys) {
			if (htblColNameValue.get(tableKey) != null) {
				allNull = false;
				break;
			}
		}
		if (allNull)
			throw new DBAppException("All null values tuple!");

		try {
			if (indexed_columns.size() == 0)
				DeletionUtilities.deleteTuples(strTableName, htblColNameValue, primaryKey, tableKeys);
			else
				DeletionUtilities.deleteTuplesIndexed(strTableName, htblColNameValue, primaryKey, indexed_columns, tableKeys);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

	}

	public void createBRINIndex(String strTableName, String strColumnName) throws Exception {
		if (!IndexUtilities.tableDirectoryExists(strTableName))
			throw new DBAppException("This table does not exist");

		String columnMeta = IndexUtilities.retrieveColumnMetaInTable(strTableName, strColumnName);
		if (IndexUtilities.isColumnIndexed(columnMeta)) {
			throw new DBAppException("An index is already created for this table");
		}
		boolean isColumnPrimary = IndexUtilities.isColumnPrimary(columnMeta);
		IndexUtilities.createBRINFiles(strTableName, strColumnName, isColumnPrimary);
	}

	@SuppressWarnings("rawtypes")
	public Iterator selectFromTable(String strTableName, String strColumnName, Object[] objarrValues,
			String[] strarrOperators) throws DBAppException {
		for (Object o : objarrValues)
			if (o == null)
				throw new DBAppException("Null Values are not allowed");
		for (Object o : strarrOperators)
			if (o == null)
				throw new DBAppException("Null Operators are not allowed");

		return SelectionUtilities.selectFromTableHelper(strTableName, strColumnName, objarrValues, strarrOperators);
	}
}
