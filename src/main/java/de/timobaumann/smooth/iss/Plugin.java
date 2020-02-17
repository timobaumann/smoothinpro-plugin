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
import inpro.incremental.unit.*;
import inpro.synthesis.hts.LoudnessPostProcessor;
import inpro.synthesis.hts.VocodingAudioStream;
import org.ros.namespace.GraphName;
import org.ros.node.*;
import org.ros.node.topic.Subscriber;
import org.xml.sax.SAXException;

import javax.swing.*;
import java.awt.*;

import java.net.URI;
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
                    @Override public Value eval(Value[] args) {
                        assert args.length == 1;
                        assert args[0].getType() == Type.String;
                        String text = ((StringValue) args[0]).getString();
                        long reference = runtime.textInputModule.addChunk(text);
                        return new IntValue(reference);
                    }
                },
                new ExecutableFunctionDescriptor("revoke_chunk", Type.Bool, new Type[]{Type.Any}) {
                    @Override public Value eval(Value[] args) {
                        assert args.length == 1;
                        assert args[0].getType() == Type.Int;
                        return new BoolValue(runtime.textInputModule.revokeChunk(((IntValue) args[0]).getInt()));
                    }
                },
                new ExecutableFunctionDescriptor("change_stress", Type.Void, new Type[]{Type.Any}) {
                    @Override public Value eval(Value[] args) {
                        assert args.length == 1;
                        assert args[0].getType() == Type.Int;
                        runtime.sp.setStress((int) ((IntValue) args[0]).getInt());
                        return Value.Void;
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

        private StressProcessor sp;

        private NodeMainExecutor nodeMainExecutor;

        private NodeMain nodeMain;

        public IssPluginRuntime() {
            Plugin.runtime = this;

            dispatcher = DispatchStream.drainingDispatchStream();

            textInputModule = new TextInputModule();
            AdaptableSynthesisModule asm = new AdaptableSynthesisModule(dispatcher);

            sp = new StressProcessor(asm);

            NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(
                    getenv("ROS_IP", "127.0.0.1"),
                    URI.create(getenv("ROS_MASTER_URI", "http://127.0.0.1:11311"))
            );
            nodeMain = new ROSNode(sp);
            nodeMainExecutor = DefaultNodeMainExecutor.newDefault();
            nodeMainExecutor.execute(nodeMain, nodeConfiguration);

            // TODO: really set a frame post processor and connect it to ROS
            asm.setFramePostProcessor(null);
            textInputModule.addListener(asm);

        }

        private String getenv(String name, String defaultValue) {
            String value = System.getenv(name);
            if (value == null || value.isEmpty()) {
                value = defaultValue;
                System.err.print("[WARN]: using default for " + name);
            }
            return value;
        }

        @Override
        public void dispose() {
            dispatcher.shutdown();
            nodeMainExecutor.shutdownNodeMain(nodeMain);
        }
    }

    private class StressProcessor {
        LoudnessPostProcessor vfpp = new LoudnessPostProcessor();
        AdaptableSynthesisModule asm;

        public StressProcessor(AdaptableSynthesisModule asm) {
            this.asm = asm;
            asm.setFramePostProcessor(vfpp);
        }


        public void setStress(int v) { // should be in the range -100 .. +100
            vfpp.setLoudness(v);
            double factor = Math.exp(v * .01 * Math.log(2)); // convert to [.5;2]
            asm.scaleTempo(factor);
            asm.shiftPitch(v * 3); // a maximum of 6 semitones?
            VocodingAudioStream.gain = Math.exp(3* (v * .01) * Math.log(2));
        }

    }

    private class ROSNode implements NodeMain {

        StressProcessor sp;

        public ROSNode(StressProcessor sp) {
            this.sp = sp;
        }

        @Override
        public GraphName getDefaultNodeName() {
            return GraphName.of("DialogOS_StressNode");
        }

        @Override
        public void onStart(final ConnectedNode connectedNode) {
            final Subscriber<std_msgs.Int32> subscriber = connectedNode.newSubscriber("DialogOS_stress", std_msgs.Int32._TYPE);
            subscriber.addMessageListener(message -> {
                int cmd = message.getData();
                sp.setStress(cmd);
            });
        }

        @Override public void onShutdown(Node node) { }
        @Override public void onShutdownComplete(Node node) { }
        @Override public void onError(Node node, Throwable throwable) { }
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
