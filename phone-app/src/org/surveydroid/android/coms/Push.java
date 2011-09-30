/*---------------------------------------------------------------------------*
 * Push.java                                                                 *
 *                                                                           *
 * Contains methods to push data from the phone to the website.              *
 *---------------------------------------------------------------------------*
 * Copyright 2011 Sema Berkiten, Vladimir Costescu, Henry Liu, Diego Vargas, *
 * Austin Walker, and Tony Xiao                                              *
 *                                                                           *
 * This file is part of Survey Droid.                                        *
 *                                                                           *
 * Survey Droid is free software: you can redistribute it and/or modify      *
 * it under the terms of the GNU General Public License as published by      *
 * the Free Software Foundation, either version 3 of the License, or         *
 * (at your option) any later version.                                       *
 *                                                                           *
 * Survey Droid is distributed in the hope that it will be useful,           *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of            *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             *
 * GNU General Public License for more details.                              *
 *                                                                           *
 * You should have received a copy of the GNU General Public License         *
 * along with Survey Droid.  If not, see <http://www.gnu.org/licenses/>.     *
 *****************************************************************************/
package org.surveydroid.android.coms;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;
import android.database.Cursor;
import android.telephony.TelephonyManager;

import org.surveydroid.android.Config;
import org.surveydroid.android.Util;
import org.surveydroid.android.database.ComsDBHandler;
import org.surveydroid.android.database.SurveyDroidDB;

/**
 * Extension of {@link WebClient} that pushes data up to the server
 *
 * @author Tony Xaio
 * @author Austin Walker
 */
public class Push
{
	//logging tag
	private static final String TAG = "Push";

	private static final String PUSH_URL = "/api/push/";

    /**
     * Push all un-uploaded survey answers in the phone database to the server.
     * Once successfully, each pushed answer will be marked as uploaded in the
     * database.
     *
     * @param ctxt - the current {@link Context}
     *
     * @return true if all the answers has been successfully pushed
     */
    public static boolean pushAnswers(Context ctxt)
    {
        Util.i(ctxt, TAG, "Pushing answers to server");
        
        TelephonyManager tManager =
        	(TelephonyManager) ctxt.getSystemService(
        			Context.TELEPHONY_SERVICE);
    	String uid = tManager.getDeviceId();
    	
    	if (uid == null)
    	{
    		Util.w(ctxt, TAG, "Device ID not available");
    		Util.w(null, TAG, "Will reschedule and try again later");
    		return false;
    	}
    	
        try
        {
        	ComsDBHandler cdbh = new ComsDBHandler(ctxt);
            cdbh.openRead();
            Cursor answers = cdbh.getNewAnswers();

            JSONArray answersJSON = new JSONArray();

            Util.d(ctxt, TAG, "# of answer to push : "
            		+ answers.getCount());
            answers.moveToFirst();

            if (answers.getCount() == 0)
            {
            	answers.close();
                cdbh.close();
            	return true;
            }
            int[] uploadedIDs = new int[answers.getCount()];
            int index = 0;

            while (!answers.isAfterLast())
            {
                JSONObject ans = new JSONObject();
                ans.put(SurveyDroidDB.AnswerTable.ANS_TYPE, answers.getInt(
                		answers.getColumnIndexOrThrow(
                				SurveyDroidDB.AnswerTable.ANS_TYPE)));
                ans.put(SurveyDroidDB.AnswerTable.CREATED, answers.getLong(
                		answers.getColumnIndexOrThrow(
                				SurveyDroidDB.AnswerTable.CREATED)));
                ans.put(SurveyDroidDB.AnswerTable.QUESTION_ID, answers.getInt(
                		answers.getColumnIndexOrThrow(
                				SurveyDroidDB.AnswerTable.QUESTION_ID)));

                //now sort what gets uploaded based on the answer type
                switch (answers.getInt(
                		answers.getColumnIndexOrThrow(
                				SurveyDroidDB.AnswerTable.ANS_TYPE)))
                {
                case SurveyDroidDB.AnswerTable.CHOICE:
                    ans.put(SurveyDroidDB.AnswerTable.CHOICE_IDS, answers.getInt(
                    		answers.getColumnIndexOrThrow(
                    				SurveyDroidDB.AnswerTable.CHOICE_IDS)));
                	break;
                case SurveyDroidDB.AnswerTable.VALUE:
                	ans.put(SurveyDroidDB.AnswerTable.ANS_VALUE, answers.getInt(
                    		answers.getColumnIndexOrThrow(
                    				SurveyDroidDB.AnswerTable.ANS_VALUE)));
                	break;
                case SurveyDroidDB.AnswerTable.TEXT:
                    ans.put(SurveyDroidDB.AnswerTable.ANS_TEXT, answers.getString(
                    		answers.getColumnIndexOrThrow(
                    				SurveyDroidDB.AnswerTable.ANS_TEXT)));
                	break;
                default:
                	throw new RuntimeException("Unknown answer type: "
                			+ answers.getInt(
                    		answers.getColumnIndexOrThrow(
                    				SurveyDroidDB.AnswerTable.ANS_TYPE)));
                }
                answersJSON.put(ans);
                uploadedIDs[index] = answers.getInt(
                		answers.getColumnIndexOrThrow(
                				SurveyDroidDB.AnswerTable._ID));
                index++;
                answers.moveToNext();
            }
            answers.close();
            cdbh.close();

            // now send to actual server
            JSONObject data = new JSONObject();

            data.put("answers", answersJSON);
            Util.d(null, TAG, data.toString());
            boolean success = WebClient.postJsonToUrl(ctxt, getPushURL(ctxt)
            		+ uid, data.toString());

            // mark answers as uploaded if appropriate
            if (success)
            {
            	cdbh.openWrite();
                for (int i = 0; i < uploadedIDs.length; i++)
                {
                    cdbh.updateAnswer(uploadedIDs[i]);
                }
                cdbh.close();
            }
            return success;
        }
        catch (Exception e)
        {
            Util.e(ctxt, TAG, Util.fmt(e));
            if (Config.D)
            	throw new RuntimeException("FATAL ERROR", e);
        }
        return false;
    }
    
    /**
     * Push all data about survey completion in the phone's database to the
     * server.  Once this is done, some of the data may be deleted.  The amount
     * deleted is determined by {@link Config#COMPLETION_SAMPLE}; enough will
     * be kept to fulfill that sample size.
     * 
     * @param ctx - the current {@link Context}
     * @return true on success
     */
    public static boolean pushCompletionData(Context ctx)
    {
    	Util.i(ctx, TAG, "Pushing survey completion data to server");
    	
    	TelephonyManager tManager =
        	(TelephonyManager) ctx.getSystemService(
        			Context.TELEPHONY_SERVICE);
    	String uid = tManager.getDeviceId();
    	
    	if (uid == null)
    	{
    		Util.w(ctx, TAG, "Device ID not available");
    		Util.w(null, TAG, "Will reschedule and try again later");
    		return false;
    	}
    	
        try
        {
            ComsDBHandler cdbh = new ComsDBHandler(ctx);
            cdbh.openRead();
            Cursor compData = cdbh.getNewCompletionData();

            JSONArray recordsJSON = new JSONArray();

            Util.d(ctx, "Push", "# of results to push : "
            		+ compData.getCount());

            if (compData.getCount() == 0)
            {
            	compData.close();
                cdbh.close();
            	return true;
            }

            compData.moveToFirst();
            int numRecords = 0;
            int[] uploadedIDs = new int[compData.getCount()];
            while (!compData.isAfterLast())
            {
                JSONObject item = new JSONObject();
                item.put(SurveyDroidDB.TakenTable.SURVEY_ID, compData.getDouble(
                		compData.getColumnIndexOrThrow(
                				SurveyDroidDB.TakenTable.SURVEY_ID)));
                item.put(SurveyDroidDB.TakenTable.STATUS, compData.getDouble(
                		compData.getColumnIndexOrThrow(
                				SurveyDroidDB.TakenTable.STATUS)));
                item.put(SurveyDroidDB.TakenTable.CREATED, compData.getDouble(
                		compData.getColumnIndexOrThrow(
                				SurveyDroidDB.TakenTable.CREATED)));
                recordsJSON.put(item);
                uploadedIDs[numRecords] = compData.getInt(
                		compData.getColumnIndexOrThrow(
                				SurveyDroidDB.TakenTable._ID));
                compData.moveToNext();
                numRecords++;
            }
            compData.close();
            cdbh.close();

            // now send to actual server
            JSONObject data = new JSONObject();

            data.put("surveysTaken", recordsJSON);
            Util.d(null, TAG, data.toString());
            boolean success = WebClient.postJsonToUrl(ctx, getPushURL(ctx)
            		+ uid, data.toString());

            // delete records if appropriate
            if (success)
            {
            	//get the number of records to keep
            	int size = Config.getSetting(ctx, Config.COMPLETION_SAMPLE,
            			Config.COMPLETION_SAMPLE_DEFAULT);
            	
            	cdbh.openWrite();
            	int numKept = 0;
                for (int i = uploadedIDs.length - 1; i >= 0; i--)
                { //remember: these are in reverse order by creation date
                    if (numKept < size)
                    {
                    	cdbh.updateCompletionRecord(uploadedIDs[i]);
                    	numKept++;
                    }
                    else
                    	cdbh.delCompletionRecord(uploadedIDs[i]);
                }
                cdbh.close();
            }
            return success;
        }
        catch (Exception e)
        {
            Util.e(ctx, TAG, Util.fmt(e));
        }
        return false;
    }

    /**
     * Push all locations in the phone database to the server. Once
     * successfully, each pushed location will be removed from the database.
     *
     * @param ctxt - the current {@link Context}
     *
     * @return true if all the locations has been successfully pushed
     */
    public static boolean pushLocations(Context ctx)
    {
        Util.i(ctx, TAG, "Pushing locations to server");
        
        TelephonyManager tManager =
        	(TelephonyManager) ctx.getSystemService(
        			Context.TELEPHONY_SERVICE);
    	String uid = tManager.getDeviceId();
    	
    	if (uid == null)
    	{
    		Util.w(ctx, TAG, "Device ID not available");
    		Util.w(null, TAG, "Will reschedule and try again later");
    		return false;
    	}
    	
        try
        {
            ComsDBHandler cdbh = new ComsDBHandler(ctx);
            cdbh.openRead();
            Cursor locations = cdbh.getLocations();

            JSONArray locationsJSON = new JSONArray();

            Util.d(ctx, "Push", "# of locations to push : "
            		+ locations.getCount());

            if (locations.getCount() == 0)
            {
            	locations.close();
                cdbh.close();
            	return true;
            }
            
            int[] uploadedIDs = new int[locations.getCount()];
            int index = 0;

            locations.moveToFirst();
            while (!locations.isAfterLast())
            {
                JSONObject loc = new JSONObject();
                loc.put(SurveyDroidDB.LocationTable.LONGITUDE, locations.getDouble(
                		locations.getColumnIndexOrThrow(
                				SurveyDroidDB.LocationTable.LONGITUDE)));
                loc.put(SurveyDroidDB.LocationTable.LATITUDE, locations.getDouble(
                		locations.getColumnIndexOrThrow(
                				SurveyDroidDB.LocationTable.LATITUDE)));
                loc.put(SurveyDroidDB.LocationTable.ACCURACY, locations.getDouble(
                		locations.getColumnIndexOrThrow(
                				SurveyDroidDB.LocationTable.ACCURACY)));
                loc.put("created", locations.getLong(
                		locations.getColumnIndexOrThrow(
                				SurveyDroidDB.LocationTable.TIME)));
                locationsJSON.put(loc);
                uploadedIDs[index] = locations.getInt(
                		locations.getColumnIndexOrThrow(
                				SurveyDroidDB.LocationTable._ID));
                index++;
                locations.moveToNext();
            }
            locations.close();
            cdbh.close();

            // now send to actual server
            JSONObject data = new JSONObject();

            data.put("locations", locationsJSON);
            Util.d(null, TAG, data.toString());
            boolean success = WebClient.postJsonToUrl(ctx, getPushURL(ctx)
            		+ uid, data.toString());

            // delete locations if appropriate
            if (success)
            {
            	cdbh.openWrite();
                for (int i = 0; i < uploadedIDs.length; i++)
                {
                    cdbh.delLocation(uploadedIDs[i]);
                }
                cdbh.close();
            }
            return success;
        }
        catch (Exception e)
        {
           Util.e(ctx, TAG, Util.fmt(e));
        }
        return false;
    }

    /**
     * Push all un-uploaded call logs in the phone database to the server
     * Phone number is hashed before sending to the server to preserve privacy
     * Once successful, each call log will be deleted from the database.
     *
     * @param ctxt - the current {@link Context}
     *
     * @return true if push was successful
     */
    public static boolean pushCallLog(Context ctx)
    {
        Util.i(ctx, TAG, "Pushing calllog to server");
        
        TelephonyManager tManager =
        	(TelephonyManager) ctx.getSystemService(
        			Context.TELEPHONY_SERVICE);
    	String uid = tManager.getDeviceId();
    	
    	if (uid == null)
    	{
    		Util.w(ctx, TAG, "Device ID not available");
    		Util.w(null, TAG, "Will reschedule and try again later");
    		return false;
    	}
    	
        try
        {
            ComsDBHandler cdbh = new ComsDBHandler(ctx);
            cdbh.openRead();
            Cursor calls = cdbh.getCalls();
            JSONArray callsJSON = new JSONArray();

           Util.d(ctx, TAG, "# of call logs to push : " + calls.getCount());

            if (calls.getCount() == 0)
            {
            	calls.close();
                cdbh.close();
            	return true;
            }
            
            int[] uploadedIDs = new int[calls.getCount()];
            int index = 0;

            calls.moveToFirst();
            while (!calls.isAfterLast())
            {
                JSONObject log = new JSONObject();
                log.put(SurveyDroidDB.CallLogTable.CALL_TYPE, calls.getString(
                		calls.getColumnIndexOrThrow(
                				SurveyDroidDB.CallLogTable.CALL_TYPE)));
                log.put(SurveyDroidDB.CallLogTable.DURATION, calls.getInt(
                		calls.getColumnIndexOrThrow(
                				SurveyDroidDB.CallLogTable.DURATION)));
                log.put(SurveyDroidDB.CallLogTable.TIME, calls.getLong(
                		calls.getColumnIndexOrThrow(
                				SurveyDroidDB.CallLogTable.TIME)));
                log.put("contact_id", hash(calls.getString(
                		calls.getColumnIndexOrThrow(
                				SurveyDroidDB.CallLogTable.PHONE_NUMBER)), ctx));
                callsJSON.put(log);
                uploadedIDs[index] = calls.getInt(
                		calls.getColumnIndexOrThrow(
                				SurveyDroidDB.CallLogTable._ID));
                index++;
                calls.moveToNext();
            }
            calls.close();
            cdbh.close();

            // now send to actual server
            JSONObject data = new JSONObject();

            data.put("calls", callsJSON);
            Util.d(null, TAG, data.toString());
            boolean success = WebClient.postJsonToUrl(ctx, getPushURL(ctx)
            		+ uid, data.toString());

            // delete calls if appropriate
            if (success)
            {
            	cdbh.openWrite();
                for (int i = 0; i < uploadedIDs.length; i++)
                {
                    cdbh.delCall(uploadedIDs[i]);
                }
                cdbh.close();
            }
            return success;
        }
        catch (Exception e)
        {
            Util.e(ctx, TAG, Util.fmt(e));
        }
        return false;
    }
    
    /**
     * Push all un-uploaded application status change record to the server.
     * Once successful, the pushed records are deleted from the database.
     *
     * @param ctxt - the current {@link Context}
     *
     * @return true if push was successful
     */
    public static boolean pushStatusData(Context ctx)
    {
    	Util.i(ctx, TAG, "Pushing status data to server");
    	
    	TelephonyManager tManager =
        	(TelephonyManager) ctx.getSystemService(
        			Context.TELEPHONY_SERVICE);
    	String uid = tManager.getDeviceId();
    	
    	if (uid == null)
    	{
    		Util.w(ctx, TAG, "Device ID not available");
    		Util.w(null, TAG, "Will reschedule and try again later");
    		return false;
    	}
    	
        try
        {
            ComsDBHandler cdbh = new ComsDBHandler(ctx);
            cdbh.openRead();
            Cursor records = cdbh.getStatusChanges();
            JSONArray recordsJSON = new JSONArray();

            Util.d(ctx, TAG, "# of status records to push : "
            			+ records.getCount());

            if (records.getCount() == 0)
            {
            	records.close();
                cdbh.close();
            	return true;
            }
            
            int[] uploadedIDs = new int[records.getCount()];
            int index = 0;

            records.moveToFirst();
            while (!records.isAfterLast())
            {
                JSONObject record = new JSONObject();
                record.put(SurveyDroidDB.StatusTable.TYPE, records.getString(
                		records.getColumnIndexOrThrow(
                				SurveyDroidDB.StatusTable.TYPE)));
                record.put(SurveyDroidDB.StatusTable.STATUS, records.getInt(
                		records.getColumnIndexOrThrow(
                				SurveyDroidDB.StatusTable.STATUS)));
                record.put(SurveyDroidDB.StatusTable.CREATED, records.getLong(
                		records.getColumnIndexOrThrow(
                				SurveyDroidDB.StatusTable.CREATED)));
                recordsJSON.put(record);
                uploadedIDs[index] = records.getInt(
                		records.getColumnIndexOrThrow(
                				SurveyDroidDB.StatusTable._ID));
                index++;
                records.moveToNext();
            }
            records.close();
            cdbh.close();

            // now send to actual server
            JSONObject data = new JSONObject();

            data.put("statusChanges", recordsJSON);
            Util.v(null, TAG, data.toString());
            boolean success = WebClient.postJsonToUrl(ctx, getPushURL(ctx)
            		+ uid, data.toString());

            // delete records if appropriate
            if (success)
            {
            	cdbh.openWrite();
                for (int i = 0; i < uploadedIDs.length; i++)
                {
                    cdbh.delStatusChange(uploadedIDs[i]);
                }
                cdbh.close();
            }
            return success;
        }
        catch (Exception e)
        {
            Util.e(ctx, TAG, Util.fmt(e));
        }
        return false;
    }
    
    //get's the full push url
    private static String getPushURL(Context c)
    {
    	StringBuilder url = new StringBuilder();
    	if (Config.getSetting(c, Config.HTTPS, Config.HTTPS_DEFAULT))
    		url.append("https://");
    	else
    		url.append("http://");
    	url.append(Config.getSetting(c, Config.SERVER, Config.SERVER_DEFAULT));
    	url.append(PUSH_URL);
    	return url.toString();
    }

    //salt and hash (for phone numbers)
    private static Integer hash(String s, Context ctxt)
    {
    	if (s == null) return null;
    	String salted = s + Config.getSetting(ctxt, Config.SALT, 0);
    	return salted.hashCode();
    }
}