package org.dmfs.provider.tasks.handler;

import org.dmfs.provider.tasks.TaskContract.Categories;
import org.dmfs.provider.tasks.TaskContract.Properties;
import org.dmfs.provider.tasks.TaskContract.Property.Category;
import org.dmfs.provider.tasks.TaskContract.Tasks;
import org.dmfs.provider.tasks.TaskDatabaseHelper.CategoriesMapping;
import org.dmfs.provider.tasks.TaskDatabaseHelper.Tables;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;


/**
 * This class is used to handle category property values during database transactions.
 * 
 * @author Tobias Reinsch <tobias@dmfs.org>
 * 
 */
public class CategoryHandler extends PropertyHandler
{

	private static final String[] CATEGORY_ID_PROJECTION = { Categories._ID, Categories.NAME, Categories.COLOR };

	private static final String CATEGORY_ID_SELECTION = Categories._ID + "=? and " + Categories.ACCOUNT_NAME + "=? and " + Categories.ACCOUNT_TYPE + "=?";
	private static final String CATEGORY_NAME_SELECTION = Categories.NAME + "=? and " + Categories.ACCOUNT_NAME + "=? and " + Categories.ACCOUNT_TYPE + "=?";

	public static final String IS_NEW_CATEGORY = "is_new_category";


	/**
	 * Validates the content of the category prior to insert and update transactions.
	 * 
	 * @param db
	 *            The {@link SQLiteDatabase}.
	 * @param isNew
	 *            Indicates that the content is new and not an update.
	 * @param values
	 *            The {@link ContentValues} to validate.
	 * @param isSyncAdapter
	 *            Indicates that the transaction was triggered from a SyncAdapter.
	 * 
	 * @return The valid {@link ContentValues}.
	 * 
	 * @throws IllegalArgumentException
	 *             if the {@link ContentValues} are invalid.
	 */
	@Override
	public ContentValues validateValues(SQLiteDatabase db, boolean isNew, ContentValues values, boolean isSyncAdapter)
	{
		// the category requires a name or an id
		if (!values.containsKey(Category.CATEGORY_ID) && !values.containsKey(Category.CATEGORY_NAME))
		{
			throw new IllegalArgumentException("Neiter an id nor a category name was supplied for the category property.");
		}

		// get the matching task & account for the property
		if (!values.containsKey(Properties.TASK_ID))
		{
			throw new IllegalArgumentException("No task id was supplied for the category property");
		}
		String[] queryArgs = { values.getAsString(Properties.TASK_ID) };
		String[] queryProjection = { Tasks.ACCOUNT_NAME, Tasks.ACCOUNT_TYPE };
		String querySelection = Tasks._ID + "=?";
		Cursor taskCursor = db.query(Tables.TASKS_VIEW, queryProjection, querySelection, queryArgs, null, null, null);

		String accountName = null;
		String accountType = null;
		try
		{
			{
				taskCursor.moveToNext();
				accountName = taskCursor.getString(0);
				accountType = taskCursor.getString(1);

				values.put(Categories.ACCOUNT_NAME, accountName);
				values.put(Categories.ACCOUNT_TYPE, accountType);

			}
		}
		finally
		{
			if (taskCursor != null)
			{
				taskCursor.close();
			}
		}

		if (accountName != null && accountType != null)
		{
			// search for matching categories
			String[] categoryArgs;
			Cursor cursor;

			if (values.containsKey(Categories._ID))
			{
				// serach by ID
				categoryArgs = new String[] { values.getAsString(Category.CATEGORY_ID), accountName, accountType };
				cursor = db.query(Tables.CATEGORIES, CATEGORY_ID_PROJECTION, CATEGORY_ID_SELECTION, categoryArgs, null, null, null);
			}
			else
			{
				// search by name
				categoryArgs = new String[] { values.getAsString(Category.CATEGORY_NAME), accountName, accountType };
				cursor = db.query(Tables.CATEGORIES, CATEGORY_ID_PROJECTION, CATEGORY_NAME_SELECTION, categoryArgs, null, null, null);
			}
			try
			{
				if (cursor != null && cursor.getCount() == 1)
				{
					cursor.moveToNext();
					Long categoryID = cursor.getLong(0);
					String categoryName = cursor.getString(1);
					int color = cursor.getInt(2);

					values.put(Category.CATEGORY_ID, categoryID);
					values.put(Category.CATEGORY_NAME, categoryName);
					values.put(Category.CATEGORY_COLOR, color);
					values.put(IS_NEW_CATEGORY, false);
				}
				else
				{
					values.put(IS_NEW_CATEGORY, true);
				}
			}
			finally
			{
				if (cursor != null)
				{
					cursor.close();
				}
			}

		}

		return values;
	}


	/**
	 * Inserts the category into the database.
	 * 
	 * @param db
	 *            The {@link SQLiteDatabase}.
	 * @param values
	 *            The {@link ContentValues} to insert.
	 * @param isSyncAdapter
	 *            Indicates that the transaction was triggered from a SyncAdapter.
	 * 
	 * @return The row id of the new category as <code>long</code>
	 */
	@Override
	public long insert(SQLiteDatabase db, ContentValues values, boolean isSyncAdapter)
	{
		values = validateValues(db, true, values, isSyncAdapter);
		values = getOrInsertCategory(db, values);
		insertRelation(db, values.getAsString(Category.TASK_ID), values.getAsString(Category.CATEGORY_ID));

		// insert property row and create relation
		return super.insert(db, values, isSyncAdapter);
	}


	/**
	 * Updates the category in the database.
	 * 
	 * @param db
	 *            The {@link SQLiteDatabase}.
	 * @param values
	 *            The {@link ContentValues} to update.
	 * @param selection
	 *            The selection <code>String</code> to update the right row.
	 * @param selectionArgs
	 *            The arguments for the selection <code>String</code>.
	 * @param isSyncAdapter
	 *            Indicates that the transaction was triggered from a SyncAdapter.
	 * 
	 * @return The number of rows affected.
	 */
	@Override
	public int update(SQLiteDatabase db, ContentValues values, String selection, String[] selectionArgs, boolean isSyncAdapter)
	{
		super.update(db, values, selection, selectionArgs, isSyncAdapter);
		values = validateValues(db, true, values, isSyncAdapter);
		values = getOrInsertCategory(db, values);

		return super.update(db, values, selection, selectionArgs, isSyncAdapter);
	}


	/**
	 * Check if a category with matching {@link ContentValues} exists and returns the existing category or creates a new category in the database.
	 * 
	 * @param db
	 *            The {@link SQLiteDatabase}.
	 * @param values
	 *            The {@link ContentValues} of the category.
	 * @return The {@link ContentValues} of the existing or new category.
	 */
	private ContentValues getOrInsertCategory(SQLiteDatabase db, ContentValues values)
	{
		if (values.getAsBoolean(IS_NEW_CATEGORY))
		{
			// insert new category in category table
			ContentValues newCategoryValues = new ContentValues();
			newCategoryValues.put(Categories.ACCOUNT_NAME, values.getAsString(Categories.ACCOUNT_NAME));
			newCategoryValues.put(Categories.ACCOUNT_TYPE, values.getAsString(Categories.ACCOUNT_TYPE));
			newCategoryValues.put(Categories.NAME, values.getAsString(Category.CATEGORY_NAME));
			newCategoryValues.put(Categories.COLOR, values.getAsInteger(Category.CATEGORY_COLOR));

			long categoryID = db.insert(Tables.CATEGORIES, "", newCategoryValues);
			values.put(Category.CATEGORY_ID, categoryID);
		}

		// remove redundant values
		values.remove(IS_NEW_CATEGORY);
		values.remove(Categories.ACCOUNT_NAME);
		values.remove(Categories.ACCOUNT_TYPE);

		return values;
	}


	/**
	 * Inserts a relation entry in the database to link task and category.
	 * 
	 * @param db
	 *            The {@link SQLiteDatabase}.
	 * @param taskId
	 *            The row id of the task.
	 * @param categoryId
	 *            The row id of the category.
	 * @return The row id of the inserted relation.
	 */
	private long insertRelation(SQLiteDatabase db, String taskId, String categoryId)
	{
		ContentValues relationValues = new ContentValues();
		relationValues.put(CategoriesMapping.TASK_ID, taskId);
		relationValues.put(CategoriesMapping.CATEGORY_ID, categoryId);
		return db.insert(Tables.CATEGORIES_MAPPING, "", relationValues);
	}
}
