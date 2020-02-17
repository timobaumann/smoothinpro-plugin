package de.timobaumann.smooth.iss;

import com.clt.dialogos.plugin.PluginRuntime;
import com.clt.dialogos.plugin.PluginSettings;

import com.clt.diamant.IdMap;
import com.clt.script.exp.ExecutableFunctionDescriptor;
import com.clt.script.exp.Type;
import com.clt.script.exp.Value;
import com.clt.script.exp.values.BoolValue;
import com.clt.script.exp.values.IntValue;
import com.clt.script.exp.values.StringValue;
import com.clt.xml.XMLReader;
import com.clt.xml.XMLWriter;
import inpro.audio.DispatchStream;
import inpro.incremental.processor.AdaptableSynthesisModule;
import inpro.incremental.source.IUSourceModule;
import inpro.incremental.unit.ChunkIU;
import inpro.incremental.unit.EditMessage;
import inpro.incremental.unit.EditType;
import inpro.incremental.unit.IU;
import org.xml.sax.SAXException;

import javax.swing.*;
import java.awt.*;

import java.util.Arrays;
import java.util.List;

public class Plugin implements com.clt.dialogos.plugin.Plugin {

    public static IssPluginRuntime runtime;

    @Override
    public void initialize() {
/*        System.setProperty("inpro.tts.language", "de");
        System.setProperty("mary.voice", "bits1-hsmm");
        System.setProperty("inpro.tts.voice", "bits1-hsmm"); /* */
/*        System.setProperty("inpro.tts.language", "en_US");
        System.setProperty("mary.voice", "cmu-slt-hsmm");
        System.setProperty("inpro.tts.voice", "cmu-slt-hsmm"); /* */

    //    System.setProperty("inpro.tts.debug", "true");

//        Node.registerNodeTypes(com.clt.speech.Resources.getResources().createLocalizedString("ScriptNode"),
//                Arrays.asList(new Class<?>[]{IssNode.class}));
    }

    @Override
    public List<ExecutableFunctionDescriptor> registerScriptFunctions() {
        return Arrays.asList(
                new ExecutableFunctionDescriptor("say_chunk", Type.Int, new Type[]{Type.Any}) {
                    @Override
                    public Value eval(Value[] args) {
                        assert args.length == 1;
                        assert args[0].getType() == Type.String;
                        String text = ((StringValue) args[0]).getString();
                        long reference = runtime.textInputModule.addChunk(text);
                        return new IntValue(reference);
                    }
                },
                new ExecutableFunctionDescriptor("revoke_chunk", Type.Bool, new Type[]{Type.Any}) {
                    @Override
                    public Value eval(Value[] args) {
                        assert args.length == 1;
                        assert args[0].getType() == Type.Int;
                        return new BoolValue(runtime.textInputModule.revokeChunk(((IntValue) args[0]).getInt()));
                    }
                }
        );
    }

    @Override
    public String getId() {
        return "de.timobaumann.smooth.iss";
    }

    @Override
    public String getName() {
        return "Incremental Speech Plugin";
    }

    @Override
    public Icon getIcon() {
        return null;
    }

    @Override
    public String getVersion() { return "0.1"; }

    @Override
    public PluginSettings createDefaultSettings() {
        return new IssPluginSettings();
    }

    private class IssPluginSettings extends PluginSettings {

        @Override
        public void writeAttributes(XMLWriter out, IdMap uidMap) { }

        @Override
        protected void readAttribute(XMLReader r, String name, String value, IdMap uid_map) throws SAXException { }

        @Override
        public JComponent createEditor() {
            return new JLabel("nothing to see here, please move on.");
        }

        @Override
        protected PluginRuntime createRuntime(Component parent) throws Exception {
            return new IssPluginRuntime();
        }
    }

    private class IssPluginRuntime implements PluginRuntime {

        //** the IU holding the incrementally synthesized installment */
        //public static SysInstallmentIU installment;
        private TextInputModule textInputModule;

        private DispatchStream dispatcher;

        public IssPluginRuntime() {
            Plugin.runtime = this;

            dispatcher = DispatchStream.drainingDispatchStream();

            textInputModule = new TextInputModule();
            AdaptableSynthesisModule asm = new AdaptableSynthesisModule(dispatcher);
            // TODO: really set a frame post processor and connect it to ROS
            asm.setFramePostProcessor(null);
            textInputModule.addListener(asm);

        }

        @Override
        public void dispose() {
            dispatcher.shutdown();
        }
    }

    private static class TextInputModule extends IUSourceModule {

        private boolean revokeChunk(long i) {
            IU last = this.rightBuffer.getBuffer().get(this.rightBuffer.getBuffer().size() - 1);
            if (last.getCreationTime() == i && last.isUpcoming()) {
                this.rightBuffer.editBuffer(new EditMessage<>(EditType.REVOKE, last));
                this.notifyListeners();
                return true;
            }
            return false;
        }

        private long addChunk(String text) {
            ChunkIU ch = new ChunkIU(text);
            this.rightBuffer.addToBuffer(ch);
            this.notifyListeners();
            return ch.getCreationTime();
        }

    }

}
