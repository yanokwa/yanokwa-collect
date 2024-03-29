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

package org.odk.collect.android.activities;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.form.api.FormEntryModel;
import org.javarosa.model.xform.XFormsModule;
import org.odk.collect.android.R;
import org.odk.collect.android.database.FileDbAdapter;
import org.odk.collect.android.listeners.FormLoaderListener;
import org.odk.collect.android.listeners.FormSavedListener;
import org.odk.collect.android.logic.GlobalConstants;
import org.odk.collect.android.logic.PropertyManager;
import org.odk.collect.android.tasks.FormLoaderTask;
import org.odk.collect.android.tasks.SaveToDiskTask;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.utilities.GestureDetector;
import org.odk.collect.android.views.QuestionView;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore.Images;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Animation.AnimationListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;


/**
 * FormEntryActivity is responsible for displaying questions, animating transitions between
 * questions, and allowing the user to enter data.
 * 
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class FormEntryActivity extends Activity implements AnimationListener, FormLoaderListener,
        FormSavedListener {
    private final String t = "FormEntryActivity";

    private static final String FORMPATH = "formpath";
    private static final String INSTANCEPATH = "instancepath";
    private static final String NEWFORM = "newform";

    private static final int MENU_CLEAR = Menu.FIRST;
    private static final int MENU_DELETE_REPEAT = Menu.FIRST + 1;
    private static final int MENU_LANGUAGES = Menu.FIRST + 2;
    private static final int MENU_HIERARCHY_VIEW = Menu.FIRST + 3;
    private static final int MENU_SUBMENU = Menu.FIRST + 4;
    private static final int MENU_SAVE = Menu.FIRST + 5;
    private static final int MENU_COMPLETE = Menu.FIRST + 6;

    private static final int PROGRESS_DIALOG = 1;
    private static final int SAVING_DIALOG = 2;

    // uncomment when ProgressBar slowdown is fixed.
    // private ProgressBar mProgressBar;

    private String mFormPath;
    private String mInstancePath;

    private GestureDetector mGestureDetector;

    public static FormEntryController mFormEntryController;
    public FormEntryModel mFormEntryModel;

    private Animation mInAnimation;
    private Animation mOutAnimation;

    private RelativeLayout mRelativeLayout;
    private View mCurrentView;

    private AlertDialog mAlertDialog;
    private ProgressDialog mProgressDialog;

    // used to limit forward/backward swipes to one per question
    private boolean mBeenSwiped;

    private FormLoaderTask mFormLoaderTask;
    private SaveToDiskTask mSaveToDiskTask;

    enum AnimationType {
        LEFT, RIGHT, FADE
    }


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.form_entry);
        setTitle(getString(R.string.app_name) + " > " + getString(R.string.enter_data));

        // mProgressBar = (ProgressBar) findViewById(R.id.progressbar);
        mRelativeLayout = (RelativeLayout) findViewById(R.id.rl);

        mBeenSwiped = false;
        mAlertDialog = null;
        mCurrentView = null;
        mInAnimation = null;
        mOutAnimation = null;
        mGestureDetector = new GestureDetector();

        // Load JavaRosa modules. needed to restore forms.
        new XFormsModule().registerModule();

        // needed to override rms property manager
        org.javarosa.core.services.PropertyManager.setPropertyManager(new PropertyManager(
                getApplicationContext()));

        Boolean newForm = true;
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(FORMPATH)) {
                mFormPath = savedInstanceState.getString(FORMPATH);
            }
            if (savedInstanceState.containsKey(INSTANCEPATH)) {
                mInstancePath = savedInstanceState.getString(INSTANCEPATH);
            }
            if (savedInstanceState.containsKey(NEWFORM)) {
                newForm = savedInstanceState.getBoolean(NEWFORM, true);
            }
        }

        // Check to see if this is a screen flip or a new form load.
        Object data = getLastNonConfigurationInstance();
        if (data instanceof FormLoaderTask) {
            mFormLoaderTask = (FormLoaderTask) data;
        } else if (data instanceof SaveToDiskTask) {
            mSaveToDiskTask = (SaveToDiskTask) data;
        } else if (data == null) {
            if (!newForm) {
                refreshCurrentView();
                return;
            }

            // Not a restart from a screen orientation change (or other).
            mFormEntryController = null;

            Intent intent = getIntent();
            if (intent != null) {
                mFormPath = intent.getStringExtra(GlobalConstants.KEY_FORMPATH);
                mInstancePath = intent.getStringExtra(GlobalConstants.KEY_INSTANCEPATH);
                mFormLoaderTask = new FormLoaderTask();
                mFormLoaderTask.execute(mFormPath, mInstancePath);
                showDialog(PROGRESS_DIALOG);
            }
        }
    }


    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(FORMPATH, mFormPath);
        outState.putString(INSTANCEPATH, mInstancePath);
        outState.putBoolean(NEWFORM, false);
    }


    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (resultCode == RESULT_CANCELED) {
            // request was canceled, so do nothing
            return;
        }

        switch (requestCode) {
            case GlobalConstants.BARCODE_CAPTURE:
                String sb = intent.getStringExtra("SCAN_RESULT");
                ((QuestionView) mCurrentView).setBinaryData(sb);
                saveCurrentAnswer(false);
                break;
            case GlobalConstants.IMAGE_CAPTURE:
                // We saved the image to the tempfile_path, but we really want
                // it to be in:
                // /sdcard/odk/instances/[current instnace]/something.jpg
                // so we move it here before inserting it into the content
                // provider.
                File fi = new File(GlobalConstants.TMPFILE_PATH);

                String mInstanceFolder =
                        mInstancePath.substring(0, mInstancePath.lastIndexOf("/") + 1);
                String s = mInstanceFolder + "/" + System.currentTimeMillis() + ".jpg";

                File nf = new File(s);
                if (!fi.renameTo(nf)) {
                    Log.i(t, "Failed to rename " + fi.getAbsolutePath());
                } else {
                    Log.i(t, "renamed " + fi.getAbsolutePath() + " to " + nf.getAbsolutePath());
                }

                // Add the new image to the Media content provider so that the
                // viewing is fast in Android 2.0+
                ContentValues values = new ContentValues(6);
                values.put(Images.Media.TITLE, nf.getName());
                values.put(Images.Media.DISPLAY_NAME, nf.getName());
                values.put(Images.Media.DATE_TAKEN, System.currentTimeMillis());
                values.put(Images.Media.MIME_TYPE, "image/jpeg");
                values.put(Images.Media.DATA, nf.getAbsolutePath());

                Uri imageuri =
                        getContentResolver().insert(Images.Media.EXTERNAL_CONTENT_URI, values);
                Log.i(t, "Inserting image returned uri = " + imageuri.toString());

                ((QuestionView) mCurrentView).setBinaryData(imageuri);
                saveCurrentAnswer(false);
                refreshCurrentView();
                break;
            case GlobalConstants.AUDIO_CAPTURE:
            case GlobalConstants.VIDEO_CAPTURE:
                Uri um = intent.getData();
                ((QuestionView) mCurrentView).setBinaryData(um);
                saveCurrentAnswer(false);
                refreshCurrentView();
                break;
            case GlobalConstants.LOCATION_CAPTURE:
                String sl = intent.getStringExtra("LOCATION_RESULT");
                ((QuestionView) mCurrentView).setBinaryData(sl);
                saveCurrentAnswer(false);
                break;
        }
    }


    /**
     * Refreshes the current view. the controller and the displayed view can get out of sync due to
     * dialogs and restarts caused by screen orientation changes, so they're resynchronized here.
     */
    public void refreshCurrentView() {
        int event = mFormEntryModel.getEvent();

        // When we refresh, if we're at a repeat prompt then step back to the
        // last question.
        while (event == FormEntryController.EVENT_PROMPT_NEW_REPEAT
                || event == FormEntryController.EVENT_GROUP
                || event == FormEntryController.EVENT_REPEAT) {
            event = mFormEntryController.stepToPreviousEvent();
        }
        Log.e(t, "refreshing view for event: " + event);

        View current = createView(event);
        showView(current, AnimationType.FADE);
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.removeItem(MENU_CLEAR);
        menu.removeItem(MENU_DELETE_REPEAT);
        menu.removeItem(MENU_LANGUAGES);
        menu.removeItem(MENU_HIERARCHY_VIEW);
        menu.removeItem(MENU_SUBMENU);

        SubMenu sm =
                menu.addSubMenu(0, MENU_SUBMENU, 0, R.string.quit_entry).setIcon(
                        android.R.drawable.ic_menu_save);
        sm.add(0, MENU_SAVE, 0, getString(R.string.save_for_later));
        sm.add(0, MENU_COMPLETE, 0, getString(R.string.finalize_for_send));

        menu.add(0, MENU_CLEAR, 0, getString(R.string.clear_answer)).setIcon(
                android.R.drawable.ic_menu_close_clear_cancel).setEnabled(
                !mFormEntryModel.isIndexReadonly() ? true : false);
        menu.add(0, MENU_DELETE_REPEAT, 0, getString(R.string.delete_repeat)).setIcon(
                R.drawable.ic_menu_clear_playlist).setEnabled(
                doesIndexContainRepeatableGroup(mFormEntryModel.getFormIndex()) ? true : false);
        menu.add(0, MENU_HIERARCHY_VIEW, 0, getString(R.string.view_hierarchy)).setIcon(
                R.drawable.ic_menu_goto);
        menu.add(0, MENU_LANGUAGES, 0, getString(R.string.change_language)).setIcon(
                R.drawable.ic_menu_start_conversation).setEnabled(
                (mFormEntryModel.getLanguages() == null || mFormEntryController.getModel()
                        .getLanguages().length == 1) ? false : true);
        return true;
    }


    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_LANGUAGES:
                createLanguageDialog();
                return true;
            case MENU_CLEAR:
                createClearDialog();
                return true;
            case MENU_DELETE_REPEAT:
                createDeleteRepeatConfirmDialog();
                return true;
            case MENU_SAVE:
                createSaveExitDialog(false);
                return true;
            case MENU_COMPLETE:
                createSaveExitDialog(true);
                return true;
            case MENU_HIERARCHY_VIEW:
                if (currentPromptIsQuestion()) {
                    saveCurrentAnswer(false);
                }
                Intent i = new Intent(this, FormHierarchyActivity.class);
                startActivity(i);
        }
        return super.onOptionsItemSelected(item);
    }


    /**
     * 
     * @return true if the current View represents a question in the form
     */
    private boolean currentPromptIsQuestion() {
        return (mFormEntryModel.getEvent() == FormEntryController.EVENT_QUESTION);
    }


    /**
     * Attempt to save the answer to the current prompt into the data model.
     * 
     * @param evaluateConstraints
     * @return true on success, false otherwise
     */
    private boolean saveCurrentAnswer(boolean evaluateConstraints) {
        if (!mFormEntryModel.isIndexReadonly()
                && mFormEntryModel.getEvent() == FormEntryController.EVENT_QUESTION) {
            int saveStatus =
                    saveAnswer(((QuestionView) mCurrentView).getAnswer(), evaluateConstraints);
            if (evaluateConstraints && saveStatus != FormEntryController.ANSWER_OK) {
                createConstraintToast(mFormEntryModel.getQuestionPrompt().getConstraintText(),
                        saveStatus);
                return false;
            }
        }
        return true;
    }


    /**
     * Clears the answer on the screen.
     */
    private void clearCurrentAnswer() {
        if (!mFormEntryModel.isIndexReadonly()) ((QuestionView) mCurrentView).clearAnswer();
    }


    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onRetainNonConfigurationInstance() If we're loading, then we pass
     * the loading thread to our next instance.
     */
    @Override
    public Object onRetainNonConfigurationInstance() {
        // if a form is loading, pass the loader task
        if (mFormLoaderTask != null && mFormLoaderTask.getStatus() != AsyncTask.Status.FINISHED)
            return mFormLoaderTask;

        // if a form is writing to disk, pass the save to disk task
        if (mSaveToDiskTask != null && mSaveToDiskTask.getStatus() != AsyncTask.Status.FINISHED)
            return mSaveToDiskTask;

        // mFormEntryController is static so we don't need to pass it.
        if (mFormEntryController != null && currentPromptIsQuestion()) {
            saveCurrentAnswer(false);
        }
        return null;
    }


    /**
     * Creates a view given the View type and a prompt
     * 
     * @param prompt
     * @return newly created View
     */
    private View createView(int event) {
        setTitle(getString(R.string.app_name) + " > " + mFormEntryModel.getFormTitle());

        switch (event) {
            case FormEntryController.EVENT_BEGINNING_OF_FORM:
                View startView = View.inflate(this, R.layout.form_entry_start, null);
                setTitle(getString(R.string.app_name) + " > " + mFormEntryModel.getFormTitle());
                ((TextView) startView.findViewById(R.id.description)).setText(getString(
                        R.string.enter_data_description, mFormEntryModel.getFormTitle()));
                return startView;
            case FormEntryController.EVENT_END_OF_FORM:
                View endView = View.inflate(this, R.layout.form_entry_end, null);
                ((TextView) endView.findViewById(R.id.description)).setText(getString(
                        R.string.save_enter_data_description, mFormEntryModel.getFormTitle()));

                // Create 'save complete' button.
                ((Button) endView.findViewById(R.id.complete_exit_button))
                        .setOnClickListener(new OnClickListener() {
                            public void onClick(View v) {
                                // Form is markd as 'done' here.
                                saveDataToDisk(true);
                            }
                        });

                // Create 'save for later' button
                ((Button) endView.findViewById(R.id.save_exit_button))
                        .setOnClickListener(new OnClickListener() {
                            public void onClick(View v) {
                                // Form is markd as 'saved' here.
                                saveDataToDisk(false);
                            }
                        });
                return endView;
            case FormEntryController.EVENT_QUESTION:
                QuestionView qv = new QuestionView(this, mInstancePath);
                qv.buildView(mFormEntryModel.getQuestionPrompt(), getGroupsForCurrentIndex());
                return qv;
            default:
                Log.e(t, "Attempted to create a view that does not exist.");
                return null;
        }
    }


    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#dispatchTouchEvent(android.view.MotionEvent)
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent mv) {
        boolean handled = onTouchEvent(mv);
        if (!handled) {
            return super.dispatchTouchEvent(mv);
        }
        return handled; // this is always true
    }


    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onTouchEvent(android.view.MotionEvent)
     */
    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        /*
         * constrain the user to only be able to swipe (that causes a view transition) once per
         * screen with the mBeenSwiped variable.
         */
        boolean handled = false;
        if (!mBeenSwiped) {
            switch (mGestureDetector.getGesture(motionEvent)) {
                case SWIPE_RIGHT:
                    mBeenSwiped = true;
                    showPreviousView();
                    handled = true;
                    break;
                case SWIPE_LEFT:
                    mBeenSwiped = true;
                    showNextView();
                    handled = true;
                    break;
            }
        }
        return handled;
    }


    /**
     * Determines what should be displayed on the screen. Possible options are: a question, an ask
     * repeat dialog, or the submit screen. Also saves answers to the data model after checking
     * constraints.
     */
    private void showNextView() {
        if (currentPromptIsQuestion()) {
            if (!saveCurrentAnswer(true)) {
                // A constraint was violated so a dialog should be showing.
                return;
            }
        }

        if (mFormEntryModel.getEvent() != FormEntryController.EVENT_END_OF_FORM) {
            int event = getNextNotGroupEvent();

            switch (event) {
                case FormEntryController.EVENT_QUESTION:
                case FormEntryController.EVENT_END_OF_FORM:
                    View next = createView(event);
                    showView(next, AnimationType.RIGHT);
                    break;
                case FormEntryController.EVENT_PROMPT_NEW_REPEAT:
                    createRepeatDialog();
                    break;
            }
        } else {
            mBeenSwiped = false;
        }
    }


    /**
     * Determines what should be displayed between a question, or the start screen and displays the
     * appropriate view. Also saves answers to the data model without checking constraints.
     */
    private void showPreviousView() {
        // The answer is saved on a back swipe, but question constraints are
        // ignored.
        if (currentPromptIsQuestion()) {
            saveCurrentAnswer(false);
        }

        if (mFormEntryModel.getEvent() != FormEntryController.EVENT_BEGINNING_OF_FORM) {
            int event = mFormEntryController.stepToPreviousEvent();

            while (event != FormEntryController.EVENT_BEGINNING_OF_FORM
                    && event != FormEntryController.EVENT_QUESTION) {
                event = mFormEntryController.stepToPreviousEvent();
            }

            View next = createView(event);
            showView(next, AnimationType.LEFT);
        } else {
            mBeenSwiped = false;
        }
    }


    /**
     * Displays the View specified by the parameter 'next', animating both the current view and next
     * appropriately given the AnimationType. Also updates the progress bar.
     */
    public void showView(View next, AnimationType from) {
        switch (from) {
            case RIGHT:
                mInAnimation = AnimationUtils.loadAnimation(this, R.anim.push_left_in);
                mOutAnimation = AnimationUtils.loadAnimation(this, R.anim.push_left_out);
                break;
            case LEFT:
                mInAnimation = AnimationUtils.loadAnimation(this, R.anim.push_right_in);
                mOutAnimation = AnimationUtils.loadAnimation(this, R.anim.push_right_out);
                break;
            case FADE:
                mInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in);
                mOutAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_out);
                break;
        }

        if (mCurrentView != null) {
            mCurrentView.startAnimation(mOutAnimation);
            mRelativeLayout.removeView(mCurrentView);
        }

        mInAnimation.setAnimationListener(this);

        // We must call setMax() first because it doesn't redraw the progress
        // bar.

        // UnComment to make progress bar work.
        // WARNING: will currently slow large forms considerably
        // TODO: make the progress bar fast. Must be done in javarosa.
        // mProgressBar.setMax(mFormEntryModel.getTotalRelevantQuestionCount());
        // mProgressBar.setProgress(mFormEntryModel.getCompletedRelevantQuestionCount());

        RelativeLayout.LayoutParams lp =
                new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
        lp.addRule(RelativeLayout.ABOVE, R.id.progressbar);

        mCurrentView = next;
        mRelativeLayout.addView(mCurrentView, lp);

        mCurrentView.startAnimation(mInAnimation);
        if (mCurrentView instanceof QuestionView)
            ((QuestionView) mCurrentView).setFocus(this);
        else {
            InputMethodManager inputManager =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(mCurrentView.getWindowToken(), 0);
        }
    }


    // TODO: use managed dialogs when the bugs are fixed
    /*
     * Ideally, we'd like to use Android to manage dialogs with onCreateDialog() and
     * onPrepareDialog(), but dialogs with dynamic content are broken in 1.5 (cupcake). We do use
     * managed dialogs for our static loading ProgressDialog.
     * 
     * The main issue we noticed and are waiting to see fixed is: onPrepareDialog() is not called
     * after a screen orientation change. http://code.google.com/p/android/issues/detail?id=1639
     */


    public void createLocationDialog() {
        mProgressDialog = new ProgressDialog(this);
        DialogInterface.OnClickListener geopointButtonListener =
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Log.i("yaw", "inside form entry cancel button");
                    }
                };
        mProgressDialog.setCancelable(false);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setTitle(getString(R.string.getting_location));
        mProgressDialog.setMessage(getString(R.string.please_wait));
        mProgressDialog.setButton(getString(R.string.cancel), geopointButtonListener);
        mProgressDialog.show();
    }


    /**
     * Creates and displays a dialog displaying the violated constraint.
     */
    private void createConstraintToast(String constraintText, int saveStatus) {
        switch (saveStatus) {
            case FormEntryController.ANSWER_CONSTRAINT_VIOLATED:
                if (constraintText == null) {
                    constraintText = getString(R.string.invalid_answer_error);
                }
                break;
            case FormEntryController.ANSWER_REQUIRED_BUT_EMPTY:
                constraintText = getString(R.string.required_answer_error);
                break;
        }

        showCustomToast(constraintText);
        mBeenSwiped = false;
    }


    private void showCustomToast(String message) {
        LayoutInflater inflater =
                (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View view = inflater.inflate(R.layout.toast_view, null);

        // set the text in the view
        TextView tv = (TextView) view.findViewById(R.id.message);
        tv.setText(message);

        Toast t = new Toast(this);
        t.setView(view);
        t.setDuration(Toast.LENGTH_SHORT);
        t.setGravity(Gravity.CENTER, 0, 0);
        t.show();
    }


    /**
     * Creates and displays a dialog asking the user if they'd like to create a repeat of the
     * current group.
     */
    private void createRepeatDialog() {
        mAlertDialog = new AlertDialog.Builder(this).create();
        if (getLastRepeatCount(getGroupsForCurrentIndex()) > 0) {
            mAlertDialog.setTitle(getString(R.string.leaving_repeat));
            mAlertDialog.setMessage(getString(R.string.add_another_repeat,
                    getLastGroupText(getGroupsForCurrentIndex())));
        } else {
            mAlertDialog.setTitle(getString(R.string.entering_repeat));
            mAlertDialog.setMessage(getString(R.string.add_repeat,
                    getLastGroupText(getGroupsForCurrentIndex())));
        }
        DialogInterface.OnClickListener repeatListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON1: // yes, repeat
                        mFormEntryController.newRepeat();
                        showNextView();
                        break;
                    case DialogInterface.BUTTON2: // no, no repeat
                        showNextView();
                        break;
                }
            }
        };
        mAlertDialog.setCancelable(false);
        mAlertDialog.setButton(getString(R.string.yes), repeatListener);
        mAlertDialog.setButton2(getString(R.string.no), repeatListener);
        mAlertDialog.show();
        mBeenSwiped = false;
    }


    /**
     * Creates and displays dialog with the given errorMsg.
     */
    private void createErrorDialog(String errorMsg, final boolean shouldExit) {
        mAlertDialog = new AlertDialog.Builder(this).create();
        mAlertDialog.setMessage(errorMsg);
        DialogInterface.OnClickListener errorListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON1:
                        if (shouldExit) {
                            finish();
                        }
                        break;
                }
            }
        };
        mAlertDialog.setCancelable(false);
        mAlertDialog.setButton(getString(R.string.ok), errorListener);
        mAlertDialog.show();
    }


    /**
     * Creates a confirm/cancel dialog for deleting repeats.
     */
    private void createDeleteRepeatConfirmDialog() {
        mAlertDialog = new AlertDialog.Builder(this).create();

        String name = getLastRepeatedGroupName(getGroupsForCurrentIndex());
        int repeatcount = getLastRepeatedGroupRepeatCount(getGroupsForCurrentIndex());
        if (repeatcount != -1) {
            name += " (" + (repeatcount + 1) + ")";
        }
        mAlertDialog.setTitle(getString(R.string.delete_repeat));
        mAlertDialog.setMessage(getString(R.string.delete_repeat_confirm, name));
        DialogInterface.OnClickListener quitListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON1: // yes
                        FormIndex validIndex = mFormEntryController.deleteRepeat();
                        mFormEntryController.jumpToIndex(validIndex);
                        showPreviousView();
                        break;
                    case DialogInterface.BUTTON2: // no
                        break;
                }
            }
        };
        mAlertDialog.setCancelable(false);
        mAlertDialog.setButton(getString(R.string.yes), quitListener);
        mAlertDialog.setButton2(getString(R.string.no), quitListener);
        mAlertDialog.show();
    }


    /**
     * Called during a 'save and exit' command. The form is not 'done' here.
     */
    private void saveDataToDisk(boolean markCompleted) {
        mSaveToDiskTask = new SaveToDiskTask();
        mSaveToDiskTask.setFormSavedListener(this);
        mSaveToDiskTask.setExportVars(mInstancePath, getApplicationContext(), markCompleted);
        mSaveToDiskTask.execute();
        showDialog(SAVING_DIALOG);
    }


    /**
     * Confirm save and quit dialog
     */
    private void createSaveExitDialog(boolean markCompleted) {
        boolean saveStatus = true;

        if (mFormEntryModel.getEvent() == FormEntryController.EVENT_QUESTION) {
            saveStatus = saveCurrentAnswer(false);
        }

        if (saveStatus) {
            saveDataToDisk(markCompleted);
        }
    }



    /**
     * Confirm quit dialog
     */
    private void createQuitDialog() {
        mAlertDialog = new AlertDialog.Builder(this).create();
        mAlertDialog.setTitle(getString(R.string.quit_application));
        mAlertDialog.setMessage(getString(R.string.entry_exit_confirm));
        DialogInterface.OnClickListener quitListener = new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON1: // yes
                        FileDbAdapter fda = new FileDbAdapter(FormEntryActivity.this);
                        fda.open();
                        Cursor c = fda.fetchFilesByPath(mInstancePath, null);
                        if (c != null && c.getCount() > 0) {
                            Log.i(t, "prevously saved");
                        } else {
                            // not previously saved, cleaning up
                            String instanceFolder =
                                    mInstancePath.substring(0, mInstancePath.lastIndexOf("/") + 1);

                            String[] projection = {Images.ImageColumns._ID};
                            Cursor ci =
                                    getContentResolver().query(Images.Media.EXTERNAL_CONTENT_URI,
                                            projection, "_data like '%" + instanceFolder + "%'",
                                            null, null);
                            int del = 0;
                            if (ci.getCount() > 0) {
                                while (ci.moveToNext()) {
                                    String id =
                                            ci
                                                    .getString(ci
                                                            .getColumnIndex(Images.ImageColumns._ID));

                                    Log.i(t, "attempting to delete unused image: "
                                            + Uri.withAppendedPath(
                                                    Images.Media.EXTERNAL_CONTENT_URI, id));
                                    del +=
                                            getContentResolver().delete(
                                                    Uri.withAppendedPath(
                                                            Images.Media.EXTERNAL_CONTENT_URI, id),
                                                    null, null);
                                }
                            }
                            c.close();
                            ci.close();

                            Log.i(t, "Deleted " + del + " images from content provider");
                            FileUtils.deleteFolder(instanceFolder);
                        }
                        // clean up cursor
                        if (c != null) {
                            c.close();
                        }

                        fda.close();
                        finish();
                        break;
                    case DialogInterface.BUTTON2: // no
                        break;
                }
            }
        };
        mAlertDialog.setCancelable(false);
        mAlertDialog.setButton(getString(R.string.delete_and_exit), quitListener);
        mAlertDialog.setButton2(getString(R.string.continue_form), quitListener);
        mAlertDialog.show();
    }


    /**
     * Confirm clear dialog
     */
    private void createClearDialog() {
        mAlertDialog = new AlertDialog.Builder(this).create();
        mAlertDialog.setTitle(getString(R.string.clear_answer));
        mAlertDialog.setMessage(getString(R.string.clearanswer_confirm));
        DialogInterface.OnClickListener quitListener = new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON1: // yes
                        clearCurrentAnswer();
                        saveCurrentAnswer(false);
                        break;
                    case DialogInterface.BUTTON2: // no
                        break;
                }
            }
        };
        mAlertDialog.setCancelable(false);
        mAlertDialog.setButton(getString(R.string.yes), quitListener);
        mAlertDialog.setButton2(getString(R.string.no), quitListener);
        mAlertDialog.show();
    }


    /**
     * Creates and displays a dialog allowing the user to set the language for the form.
     */
    private void createLanguageDialog() {
        final String[] languages = mFormEntryModel.getLanguages();
        int selected = -1;
        if (languages != null) {
            String language = mFormEntryModel.getLanguage();
            for (int i = 0; i < languages.length; i++) {
                if (language.equals(languages[i])) {
                    selected = i;
                }
            }
        }
        mAlertDialog =
                new AlertDialog.Builder(this).setSingleChoiceItems(languages, selected,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                mFormEntryController.setLanguage(languages[whichButton]);
                                dialog.dismiss();
                                if (currentPromptIsQuestion()) {
                                    saveCurrentAnswer(false);
                                }
                                refreshCurrentView();
                            }
                        }).setTitle(getString(R.string.change_language)).setNegativeButton(
                        getString(R.string.cancel), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                            }
                        }).create();
        mAlertDialog.show();
    }


    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onCreateDialog(int)
     */
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case PROGRESS_DIALOG:
                mProgressDialog = new ProgressDialog(this);
                DialogInterface.OnClickListener loadingButtonListener =
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                mFormLoaderTask.setFormLoaderListener(null);
                                mFormLoaderTask.cancel(true);
                                finish();
                            }
                        };
                mProgressDialog.setTitle(getString(R.string.loading_form));
                mProgressDialog.setMessage(getString(R.string.please_wait));
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                mProgressDialog.setButton(getString(R.string.cancel), loadingButtonListener);
                return mProgressDialog;
            case SAVING_DIALOG:
                mProgressDialog = new ProgressDialog(this);
                DialogInterface.OnClickListener savingButtonListener =
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                mSaveToDiskTask.setFormSavedListener(null);
                                mSaveToDiskTask.cancel(true);
                            }
                        };
                mProgressDialog.setTitle(getString(R.string.saving_form));
                mProgressDialog.setMessage(getString(R.string.please_wait));
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                mProgressDialog.setButton(getString(R.string.cancel), savingButtonListener);
                return mProgressDialog;

        }
        return null;
    }


    /**
     * Dismiss any showing dialogs
     */
    private void dismissDialogs() {
        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            mAlertDialog.dismiss();
        }
    }


    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onPause()
     */
    @Override
    protected void onPause() {
        Log.d(t, "onPause");
        dismissDialogs();
        super.onPause();
    }


    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
        Log.d(t, "onResume");
        if (mFormLoaderTask != null) {
            mFormLoaderTask.setFormLoaderListener(this);
            if (mFormLoaderTask.getStatus() == AsyncTask.Status.FINISHED) {
                dismissDialog(PROGRESS_DIALOG);
                refreshCurrentView();
            }
        }
        if (mSaveToDiskTask != null) {
            mSaveToDiskTask.setFormSavedListener(this);
        }
        super.onResume();
    }



    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onKeyDown(int, android.view.KeyEvent)
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                createQuitDialog();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (event.isAltPressed() && !mBeenSwiped) {
                    mBeenSwiped = true;
                    showNextView();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (event.isAltPressed() && !mBeenSwiped) {
                    mBeenSwiped = true;
                    showPreviousView();
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }


    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onDestroy()
     */
    @Override
    protected void onDestroy() {
        if (mFormLoaderTask != null) mFormLoaderTask.setFormLoaderListener(null);
        if (mSaveToDiskTask != null) mSaveToDiskTask.setFormSavedListener(null);
        super.onDestroy();
    }


    /*
     * (non-Javadoc)
     * 
     * @see android.view.animation.Animation.AnimationListener#onAnimationEnd(android
     * .view.animation.Animation)
     */
    public void onAnimationEnd(Animation arg0) {
        mBeenSwiped = false;
    }


    /*
     * (non-Javadoc)
     * 
     * @see android.view.animation.Animation.AnimationListener#onAnimationRepeat(
     * android.view.animation.Animation)
     */
    public void onAnimationRepeat(Animation animation) {
        // Added by AnimationListener interface.
    }


    /*
     * (non-Javadoc)
     * 
     * @see android.view.animation.Animation.AnimationListener#onAnimationStart(android
     * .view.animation.Animation)
     */
    public void onAnimationStart(Animation animation) {
        // Added by AnimationListener interface.
    }


    /**
     * loadingComplete() is called by FormLoaderTask once it has finished loading a form.
     */
    public void loadingComplete(FormEntryController fec) {
        dismissDialog(PROGRESS_DIALOG);
        if (fec == null) {
            createErrorDialog(getString(R.string.load_error, mFormPath.substring(mFormPath
                    .lastIndexOf('/') + 1)), true);
        } else {
            mFormEntryController = fec;
            mFormEntryModel = fec.getModel();

            // Set saved answer path
            if (mInstancePath == null) {

                // Create new answer folder.
                String time =
                        new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Calendar.getInstance()
                                .getTime());
                String file =
                        mFormPath.substring(mFormPath.lastIndexOf('/') + 1, mFormPath
                                .lastIndexOf('.'));
                String path = GlobalConstants.INSTANCES_PATH + file + "_" + time;
                if (FileUtils.createFolder(path)) {
                    mInstancePath = path + "/" + file + "_" + time + ".xml";
                }
            } else {
                // we've just loaded a saved form, so start in the hierarchy
                // view
                Intent i = new Intent(this, FormHierarchyActivity.class);
                startActivity(i);
                return; // so we don't show the intro screen before jumping to
                // the hierarchy
            }

            refreshCurrentView();
        }
    }


    public void savingComplete(int saveStatus) {
        dismissDialog(SAVING_DIALOG);
        switch (saveStatus) {
            case SaveToDiskTask.SAVED:
                Toast.makeText(getApplicationContext(), getString(R.string.data_saved_ok),
                        Toast.LENGTH_SHORT).show();
                finish();
                break;
            case SaveToDiskTask.SAVE_ERROR:
                Toast.makeText(getApplicationContext(), getString(R.string.data_saved_error),
                        Toast.LENGTH_LONG).show();
                break;
            case FormEntryController.ANSWER_CONSTRAINT_VIOLATED:
            case FormEntryController.ANSWER_REQUIRED_BUT_EMPTY:
                refreshCurrentView();
                createConstraintToast(mFormEntryModel.getQuestionPrompt().getConstraintText(),
                        saveStatus);
                break;
        }
    }


    public int saveAnswer(IAnswerData answer, boolean evaluateConstraints) {
        if (evaluateConstraints) {
            return mFormEntryController.answerQuestion(answer);
        } else {
            mFormEntryController.saveAnswer(mFormEntryModel.getFormIndex(), answer);
            return FormEntryController.ANSWER_OK;
        }
    }


    private FormEntryCaption[] getGroupsForCurrentIndex() {
        if (!(mFormEntryModel.getEvent() == FormEntryController.EVENT_QUESTION || mFormEntryModel
                .getEvent() == FormEntryController.EVENT_PROMPT_NEW_REPEAT)) return null;

        int lastquestion = 1;
        if (mFormEntryModel.getEvent() == FormEntryController.EVENT_PROMPT_NEW_REPEAT)
            lastquestion = 0;
        FormEntryCaption[] v = mFormEntryModel.getCaptionHierarchy();
        FormEntryCaption[] groups = new FormEntryCaption[v.length - lastquestion];
        for (int i = 0; i < v.length - lastquestion; i++) {
            groups[i] = v[i];
        }
        return groups;
    }


    /**
     * Loops through the FormEntryController until a non-group event is found.
     * 
     * @return The event found
     */
    private int getNextNotGroupEvent() {
        int event = mFormEntryController.stepToNextEvent();

        while (event == FormEntryController.EVENT_GROUP
                || event == FormEntryController.EVENT_REPEAT) {
            event = mFormEntryController.stepToNextEvent();
        }
        return event;
    }


    /**
     * The repeat count of closest group the prompt belongs to.
     */
    private int getLastRepeatCount(FormEntryCaption[] groups) {
        // no change
        if (getLastGroup(groups) != null) {
            return getLastGroup(groups).getMultiplicity();
        }
        return -1;

    }


    /**
     * The text of closest group the prompt belongs to.
     */
    private String getLastGroupText(FormEntryCaption[] groups) {
        // no change
        if (getLastGroup(groups) != null) {
            return getLastGroup(groups).getLongText();
        }
        return null;
    }


    /**
     * The closest group the prompt belongs to.
     * 
     * @return FormEntryCaption
     */
    private FormEntryCaption getLastGroup(FormEntryCaption[] groups) {
        if (groups == null || groups.length == 0)
            return null;
        else
            return groups[groups.length - 1];
    }


    /**
     * The name of the closest group that repeats or null.
     */
    private String getLastRepeatedGroupName(FormEntryCaption[] groups) {
        // no change
        if (groups.length > 0) {
            for (int i = groups.length - 1; i > -1; i--) {
                if (groups[i].repeats()) {
                    return groups[i].getLongText();
                }
            }
        }
        return null;
    }


    /**
     * The count of the closest group that repeats or -1.
     */
    private int getLastRepeatedGroupRepeatCount(FormEntryCaption[] groups) {
        if (groups.length > 0) {
            for (int i = groups.length - 1; i > -1; i--) {
                if (groups[i].repeats()) {
                    return groups[i].getMultiplicity();

                }
            }
        }
        return -1;
    }


    private boolean doesIndexContainRepeatableGroup(FormIndex index) {
        FormEntryCaption[] groups = mFormEntryModel.getCaptionHierarchy();
        if (groups.length == 0) {
            return false;
        }
        for (int i = 0; i < groups.length; i++) {
            if (groups[i].repeats()) return true;
        }
        return false;
    }

}
