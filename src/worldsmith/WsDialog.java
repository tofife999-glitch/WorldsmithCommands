package worldsmith;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import org.lwjgl.input.Keyboard;

import java.util.Map;

/**
 * A small dialog with option buttons, opened through WsUtil.openDialog. Never stored in the save: the game
 * closes every dialog before it saves, and nothing keeps a reference to it afterwards.
 */
public abstract class WsDialog implements InteractionDialogPlugin {
    /** Option data of the button that closes the dialog without doing anything. */
    protected static final Object CANCEL = new Object();
    protected InteractionDialogAPI dialog;

    /** Shows the first page. */
    protected abstract void start();

    /** Called with the data of the chosen option (never with CANCEL). */
    protected abstract void chosen(Object data);

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        dialog.setPromptText("");
        start();
    }

    /** Clears the dialog and shows a new text; add options afterwards. */
    protected void page(String text) {
        dialog.getTextPanel().clear();
        dialog.getOptionPanel().clearOptions();
        for (String para : text.split("\n")) dialog.getTextPanel().addPara(para);
    }

    protected void option(String label, Object data) {
        dialog.getOptionPanel().addOption(label, data);
    }

    /** The Cancel button, also bound to Escape. */
    protected void cancelOption(String label) {
        dialog.getOptionPanel().addOption(label, CANCEL);
        dialog.getOptionPanel().setShortcut(CANCEL, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    protected void close() {
        dialog.dismiss();
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData == null) return;
        if (optionData == CANCEL) {
            close();
            return;
        }
        chosen(optionData);
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
    }

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return null;
    }
}
