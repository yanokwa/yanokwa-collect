/*
 * Copyright (C) 2009 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.views;

import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.widgets.IBinaryWidget;
import org.odk.collect.android.widgets.IQuestionWidget;
import org.odk.collect.android.widgets.WidgetFactory;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;


/**
 * Responsible for using a {@link FormEntryQuestion} and based on the question type
 * and answer type, displaying the appropriate widget. The class also sets (but
 * does not save) and gets the answers to questions.
 * 
 * @author Yaw Anokwa (yanokwa@gmail.com)
 * @author Carl Hartung (carlhartung@gmail.com)
 */

public class QuestionView extends ScrollView {
    private final static String t = "QuestionView";

    private IQuestionWidget mQuestionWidget;
    private LinearLayout mView;
    private LinearLayout.LayoutParams mLayout;
    private String mInstancePath;
    private final static int TEXTSIZE = 21;


    public QuestionView(Context context, String instancePath) {
        super(context);

        this.mInstancePath = instancePath;
    }


    /**
     * Create the appropriate view given your prompt.
     */
    public void buildView(FormEntryPrompt p, FormEntryCaption[] groups) {

Log.e("carl", "current entry prompt is null? " + (p == null));
        mView = new LinearLayout(getContext());
        mView.setOrientation(LinearLayout.VERTICAL);
        mView.setGravity(Gravity.TOP);
        mView.setPadding(0, 7, 0, 0);

        mLayout =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        mLayout.setMargins(10, 0, 10, 0);

        // display which group you are in as well as the question
        AddGroupText(groups);
        AddQuestionText(p);
        AddHelpText(p);

        // if question or answer type is not supported, use text widget
        mQuestionWidget = WidgetFactory.createWidgetFromPrompt(p, getContext(), mInstancePath);

        mView.addView((View) mQuestionWidget, mLayout);
        addView(mView);
    }


    public IAnswerData getAnswer() {
        return mQuestionWidget.getAnswer();
    }


    public void setBinaryData(Object answer) {
        if (mQuestionWidget instanceof IBinaryWidget)
            ((IBinaryWidget) mQuestionWidget).setBinaryData(answer);
        else
            Log.e(t, "Attempted to setBinaryData() on a non-binary widget ");
    }


    public void clearAnswer() {
        mQuestionWidget.clearAnswer();
    }


    /**
     * Add a TextView containing the hierarchy of groups to which the question
     * belongs.
     */
    private void AddGroupText(FormEntryCaption[] groups) {
        StringBuffer s = new StringBuffer("");
        String t = "";
        int i;

        // list all groups in one string
        for (FormEntryCaption g : groups) {
            i = g.getMultiplicity() + 1;
            t = g.getLongText();
            if (t != null) {
                s.append(t);
                if (g.repeats() && i > 0) {
                    s.append(" (" + i + ")");
                }
                s.append(" > ");
            }
        }

        // build view
        if (s.length() > 0) {
            TextView tv = new TextView(getContext());
            tv.setText(s.substring(0, s.length() - 3));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, TEXTSIZE - 7);
            tv.setPadding(0, 0, 0, 5);
            mView.addView(tv, mLayout);
        }
    }


    /**
     * Add a TextView containing the question text.
     */
    private void AddQuestionText(FormEntryPrompt p) {
        TextView tv = new TextView(getContext());
        tv.setText(p.getLongText());
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, TEXTSIZE);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(0, 0, 0, 7);

        // wrap to the widget of view
        tv.setHorizontallyScrolling(false);
        mView.addView(tv, mLayout);
    }


    /**
     * Add a TextView containing the help text.
     */
    private void AddHelpText(FormEntryPrompt p) {
        String s = p.getHelpText();

        if (s != null && !s.equals("")) {
            TextView tv = new TextView(getContext());
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, TEXTSIZE - 5);
            tv.setPadding(0, -5, 0, 7);
            // wrap to the widget of view
            tv.setHorizontallyScrolling(false);
            tv.setText(s);
            tv.setTypeface(null, Typeface.ITALIC);

            mView.addView(tv, mLayout);
        }
    }


    public void setFocus(Context context) {
        mQuestionWidget.setFocus(context);
    }
    
  
}
