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

package org.odk.collect.android.widgets;

import java.util.Vector;

import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.SelectOneData;
import org.javarosa.core.model.data.helper.Selection;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.logic.GlobalConstants;

import android.content.Context;
import android.util.TypedValue;
import android.view.inputmethod.InputMethodManager;
import android.widget.RadioButton;
import android.widget.RadioGroup;

/**
 * SelectOneWidgets handles select-one fields using radio buttons.
 * 
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class SelectOneWidget extends RadioGroup implements IQuestionWidget {

    private int mRadioChecked = -1;
    Vector<SelectChoice> mItems;


    public SelectOneWidget(Context context) {
        super(context);
    }


    public void clearAnswer() {
        clearCheck();
    }


    public IAnswerData getAnswer() {
        int i = getCheckedRadioButtonId();
        if (i == -1) {
            return null;
        } else {
            String s = mItems.elementAt(i).getValue();
            return new SelectOneData(new Selection(s));
        }
    }


    @SuppressWarnings("unchecked")
    public void buildView(final FormEntryPrompt prompt) {
        mItems = prompt.getSelectChoices();

        setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (mRadioChecked != -1 && prompt.isReadOnly()) {
                    SelectOneWidget.this.check(mRadioChecked);
                }
            }
        });

        String s = null;
        if (prompt.getAnswerValue() != null) {
            s = ((Selection)prompt.getAnswerValue().getValue()).getValue();
        }

        if (prompt.getSelectChoices() != null) {
            for (int i = 0; i < mItems.size(); i++) {
                RadioButton r = new RadioButton(getContext());
                r.setText(mItems.get(i).getCaption());
                r.setTextSize(TypedValue.COMPLEX_UNIT_PX, GlobalConstants.APPLICATION_FONTSIZE);
                r.setId(i);
                r.setEnabled(!prompt.isReadOnly());
                r.setFocusable(!prompt.isReadOnly());
                addView(r);

                if (mItems.get(i).getValue().equals(s)) {
                    r.setChecked(true);
                    mRadioChecked = i;
                }

            }
        }
            
            /*
            OrderedHashtable h = prompt.getSelectItems();
            Enumeration e = h.keys();
            String k = null;
            String v = null;

            // android radio ids start at 1, not 0
            int i = 1;
            while (e.hasMoreElements()) {
                k = (String) e.nextElement();
                v = (String) h.get(k);

                RadioButton r = new RadioButton(getContext());
                r.setText(k);
                r.setTextSize(TypedValue.COMPLEX_UNIT_PX, GlobalConstants.APPLICATION_FONTSIZE);
                r.setId(i);
                r.setEnabled(!prompt.isReadOnly());
                r.setFocusable(!prompt.isReadOnly());
                addView(r);

                if (v.equals(s)) {
                    r.setChecked(true);
                    mRadioChecked = i;
                }

                i++;
            }
        }
        */
    }


    public void setFocus(Context context) {
        // Hide the soft keyboard if it's showing.
        InputMethodManager inputManager =
                (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);
    }

}
