/*
 * Copyright (C) 2009 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.tasks;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.services.transport.payload.ByteArrayPayload;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.form.api.FormEntryModel;
import org.javarosa.model.xform.XFormSerializingVisitor;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.database.FileDbAdapter;
import org.odk.collect.android.listeners.FormSavedListener;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Background task for loading a form.
 * 
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class SaveToDiskTask extends AsyncTask<Void, String, Integer> {

    private FormSavedListener mSavedListener;
    private String mInstancePath;
    private Context mContext;
    private Boolean mMarkCompleted;
    private FormEntryController mFormEntryController = FormEntryActivity.mFormEntryController;

    public static final int SAVED = 500;
    public static final int SAVE_ERROR = 501;
    public static final int VALIDATE_ERROR = 502;
    public static final int VALIDATED = 503;


    /**
     * Initialize {@link FormEntryController} with {@link FormDef} from binary or from XML. If given
     * an instance, it will be used to fill the {@link FormDef}.
     */
    @Override
    protected Integer doInBackground(Void... nothing) {
        int validateStatus = validateAnswers(mMarkCompleted);
        if (validateStatus != VALIDATED) {
            return validateStatus;
        }

        mFormEntryController.getModel().getForm().postProcessInstance();
        if (exportData(mInstancePath, mContext, mMarkCompleted)) {
            return SAVED;
        }
        return SAVE_ERROR;
    }


    public boolean exportData(String instancePath, Context context, boolean markCompleted) {

        ByteArrayPayload payload;
        try {

            // assume no binary data inside the model.
            FormInstance datamodel = mFormEntryController.getModel().getForm().getInstance();
            XFormSerializingVisitor serializer = new XFormSerializingVisitor();
            payload = (ByteArrayPayload) serializer.createSerializedPayload(datamodel);

            // write out xml
            exportXmlFile(payload, instancePath);

        } catch (IOException e) {
            Log.e("savetodisk", "Error creating serialized payload");
            e.printStackTrace();
            return false;
        }

        FileDbAdapter fda = new FileDbAdapter(context);
        fda.open();
        File f = new File(instancePath);
        Cursor c = fda.fetchFilesByPath(f.getAbsolutePath(), null);
        if (!markCompleted) {
            if (c != null && c.getCount() == 0) {
                fda.createFile(instancePath, FileDbAdapter.TYPE_INSTANCE,
                        FileDbAdapter.STATUS_INCOMPLETE);
            } else {
                fda.updateFile(instancePath, FileDbAdapter.STATUS_INCOMPLETE);
            }
        } else {
            if (c != null && c.getCount() == 0) {
                fda.createFile(instancePath, FileDbAdapter.TYPE_INSTANCE,
                        FileDbAdapter.STATUS_COMPLETE);

            } else {
                fda.updateFile(instancePath, FileDbAdapter.STATUS_COMPLETE);
            }
        }
        // clean up cursor
        if (c != null) {
            c.close();
        }

        fda.close();
        return true;


    }


    private boolean exportXmlFile(ByteArrayPayload payload, String path) {

        // create data stream
        InputStream is = payload.getPayloadStream();
        int len = (int) payload.getLength();

        // read from data stream
        byte[] data = new byte[len];
        try {
            int read = is.read(data, 0, len);
            if (read > 0) {
                // write xml file
                try {
                    // String filename = path + "/" +
                    // path.substring(path.lastIndexOf('/') + 1) + ".xml";
                    BufferedWriter bw = new BufferedWriter(new FileWriter(path));
                    bw.write(new String(data, "UTF-8"));
                    bw.flush();
                    bw.close();
                    return true;

                } catch (IOException e) {
                    Log.e("savetodisk", "Error writing XML file");
                    e.printStackTrace();
                    return false;
                }
            }
        } catch (IOException e) {
            Log.e("savetodisk", "Error reading from payload data stream");
            e.printStackTrace();
            return false;
        }

        return false;

    }


    @Override
    protected void onPostExecute(Integer result) {
        synchronized (this) {
            if (mSavedListener != null) mSavedListener.savingComplete(result);
        }
    }


    public void setFormSavedListener(FormSavedListener fsl) {
        synchronized (this) {
            mSavedListener = fsl;
        }
    }


    public void setExportVars(String instancePath, Context context, Boolean completed) {
        mInstancePath = instancePath;
        mContext = context;
        mMarkCompleted = completed;
    }


    /**
     * Goes through the entire form to make sure all entered answers comply with their constraints.
     * Constraints are ignored on 'jump to', so answers can be outside of constraints. We don't
     * allow saving to disk, though, until all answers conform to their constraints/requirements.
     * 
     * @param markCompleted
     * @return
     */

    private int validateAnswers(boolean markCompleted) {
        mFormEntryController.jumpToIndex(FormIndex.createBeginningOfFormIndex());
        FormEntryModel fem = mFormEntryController.getModel();

        int event;
        while ((event = mFormEntryController.stepToNextEvent()) != FormEntryController.EVENT_END_OF_FORM) {
            if (event != FormEntryController.EVENT_QUESTION) {
                continue;
            } else {
                int saveStatus =
                        mFormEntryController.answerQuestion(fem.getQuestionPrompt()
                                .getAnswerValue());
                if (saveStatus == FormEntryController.ANSWER_CONSTRAINT_VIOLATED
                        || (markCompleted && saveStatus != FormEntryController.ANSWER_OK)) {
                    return saveStatus;
                }
            }
        }
        return VALIDATED;
    }
}
