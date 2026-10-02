package com.malfreyt.alexandre.pops_app;

import android.app.assist.AssistStructure;
import android.content.Intent;
import android.os.CancellationSignal;
import android.service.autofill.AutofillService;
import android.service.autofill.FillCallback;
import android.service.autofill.FillRequest;
import android.service.autofill.SaveCallback;
import android.service.autofill.SaveRequest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Runs in the test APK's own process, using only framework classes. Never reads field contents. */
public class AutofillProbeService extends AutofillService {
    public static final String ACTION = "com.malfreyt.alexandre.pops_app.AUTOFILL_PROBE";
    @Override public void onConnected() { android.util.Log.i("PoPS-Autofill-Test", "Probe connected"); }
    @Override public void onFillRequest(FillRequest request, CancellationSignal signal, FillCallback callback) {
        Set<String> hints = new HashSet<>();
        AssistStructure structure = request.getFillContexts().get(request.getFillContexts().size() - 1).getStructure();
        for (int i = 0; i < structure.getWindowNodeCount(); i++) visit(structure.getWindowNodeAt(i).getRootViewNode(), hints);
        android.util.Log.i("PoPS-Autofill-Test", "Probe received hints: " + hints);
        sendBroadcast(new Intent(ACTION).setPackage("com.malfreyt.alexandre.pops_app")
            .putExtra("username", hints.contains("username")).putExtra("password", hints.contains("password")));
        callback.onSuccess(null);
    }
    private void visit(AssistStructure.ViewNode node, Set<String> hints) {
        if (node.getAutofillHints() != null) hints.addAll(Arrays.asList(node.getAutofillHints()));
        for (int i = 0; i < node.getChildCount(); i++) visit(node.getChildAt(i), hints);
    }
    @Override public void onSaveRequest(SaveRequest request, SaveCallback callback) { callback.onSuccess(); }
}
