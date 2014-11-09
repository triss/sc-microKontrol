NativeMicroKontrol {
    // sysex in and out
    var midiOut, sysexIn, noteOnIn, noteOffIn;

    // midi channel we're sending and recieving on
    var midiChannel;

    // actions for incoming values
    var >noteOnAction, >noteOffAction;

    var >padOnAction, >padOffAction;
    
    var >knobAction, >bigKnobAction, >sliderAction;

    var >settingAction, >messageAction, 
        >sceneAction, >exitAction,
        >hexLockAction, >enterAction,
        >octaveUpAction, >octaveDownAction;

    *new { 
        arg globalMidiChannel = 1,
        outDeviceName = "microKONTROL-microKONTROL MIDI 2",
        outPortName = "microKONTROL-microKONTROL MIDI 2",
        inDeviceName = "microKONTROL-microKONTROL MIDI 3",
        inPortName = "microKONTROL-microKONTROL MIDI 3";

        ^super.new.init(
            globalMidiChannel, 
            outDeviceName, outPortName,
            inDeviceName, inPortName
        );
    }

    init { 
        arg globalMidiChannel, 
        outDeviceName, outPortName,
        inDeviceName, inPortName;

        var midiInPort;

        // sysex messages require midi channel is embedded in it +63
        midiChannel = globalMidiChannel + 63;

        // create an output to chat sysex on
        midiOut = MIDIOut.newByName(outDeviceName, outPortName);

        // find the MIDI in ID to listen on
        midiInPort = MIDIIn.findPort(inDeviceName, inPortName).asMIDIInPortUID;

        // handle incoming sysex
        sysexIn = MIDIFunc.sysex({ |...data|
            data = data[0].drop(5);

            switch(data[0], 
                67, { // knobs 
                    var value = data[2];

                    // make left turns negative
                    if(value > 63) {
                        value = 128 - value * -1;
                    };

                    // if knob is number 8 call big knob action
                    if(data[1] == 8) {
                        bigKnobAction.(value);
                    } {
                        knobAction.(data[1], value);
                    };
                },

                68, { // sliders
                    sliderAction.(data[1], data[2]);
                },

                69, { // pads
                    if(data[1] < 64) {
                        padOffAction.(data[1])
                    } {
                        padOnAction.(data[1] - 64)
                    };
                },

                72, {
                   data.postln; 
                }
            )
        }, midiInPort);

        // handle incoming note on
        noteOnIn = MIDIFunc.noteOn({

        }, midiInPort);

        // handle incoming note off
        noteOffIn = MIDIFunc.noteOff({

        }, midiInPort);
    }

    // sends a message that sets pad led state
    //   pad  -> the pad to update 0 - 15
    //   type -> the update type 0 - off, 1 - on, 2 - oneshot, 3 - blink 
    //   rate -> blink rate in milliseconds
    setPadLed { |pad=0 type=0 rate=100|
        midiOut.sysex(
            Int8Array[
                240, 66, midiChannel, 110, 0, 1, pad, 
                // type and rate are bit shifted together
                type << 5 + rate.div(9), 247
            ]
        );
    }

    // sends a message that sets display status
    //   display -> the display to update 0 - 8
    //   colour  -> the colour to display:
    //              0 - clear, 1 - red, 2 - green, 3 - orange
    //   string  -> the message to display
    setDisplay { |display=0 colour=1 string=""|
        var msg, chars = 32 ! 8;

        string.padLeft(8).do { |c i| chars[i] = c.ascii };
        
        msg = [
            240, 66, midiChannel, 110, 0, 34, 9, 
            colour << 4 + display,
            chars,
            247
        ].flatten;

        midiOut.sysex(Int8Array.with(*msg));
    }

    // disables native mode on MicroKontrol
    disableNativeMode {
        // set the sysex mode 
        sysexIn.func = {
            if(data[0] == Int8Array[240, 66, 64, 110, 0, 64, 0, 2, 247]) {
               "MicroKontrol has left Native Mode.".postln;
            };
        };

        midiOut.sysex(Int8Array[240, 66, midiChannel, 110, 0, 0, 0, 0, 247]);
    }
}
