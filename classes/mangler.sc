Mangler {
	var w,w2,<group,definitions,path,midichan,modwheel,modspec,cbus,dry,activeEffects,noteText;
	
	*new { |definitions|
		^super.new.init(definitions);
	}
	
	init { |d|
		definitions = d;

		// MIDI setup for your system
		midichan = 0;
		modwheel = 1;
		
		activeEffects = 0;
		modspec = \midi.asSpec;		
		
		// pr. function
		noteText = { |note|
			(
				60: "C3",
				62: "D3",
				64: "E3",
				65: "F3",
				67: "G3"
			)[note].asString;
		};
						
		w = SCWindow( "The Mangler", Rect( 400,855,625,110 ), resizable: false);
		w.view.decorator = FlowLayout(w.view.bounds);
		w.onClose_{ MIDIResponder.removeAll; w2.close };
		
		w2 = SCWindow( "Signal Flow", Rect(1000,400,150,200), resizable: false);
		w2.view.decorator = FlowLayout(w2.view.bounds);
		path = SCStaticText(w2, Rect(10,10,150,200) )
			.background_( Color.yellow )
			.stringColor_( Color.black )
			.font_(Font("MarkerFelt-Thin", 20))
			;

		group = Group.new;
		
		this.initMangles;
		this.drawMangles;
		this.drawSignalPath;
		this.initMIDI;
		
		
		
		Synth.head(group, \manglerBase);
		
		w.front;
		w2.front;
	}
		
	initMIDI {
		NoteOnResponder { |... args| this.switchNode(true, *args) };
		NoteOffResponder{ |... args| this.switchNode(false, *args) };
		CCResponder{ |port,chan,cc,val|
			if(cc == modwheel, {
				definitions.do{ |def|
					def[\synth].set(def[\param], def[\spec].map(modspec.unmap(val)));
				}
			})
		};
	}
	
	switchNode { |running=true,port,chan,note,vel|
		if(chan == midichan, {
			var definition;
			definition = definitions.detect{ |def| def[\midinote] == note };
			definition !? { 
				definition[\synth].set(\gate, if(running, 1, 0));
				
				// keep a count of active effects, so we can drop dry back in if it's 0
				if(definition[\cutdry], {
					activeEffects = activeEffects + if(running, 1, - 1);
				});

				dry.set(\gate, if(activeEffects == 0, 1, 0));
				
				{ 
					definition[\button].background_( if(running, { Color.green }, { Color(0.8,0,0) }) );
					definition[\button].stringColor_( if(running, { Color.black }, { Color.white }) );
				}.defer;
			};
		});
	}
	
	initMangles {
		// set up dry out
		dry = Synth.tail(group, \mangler_dryout);
	
		definitions.do { |def, n| 
			var synth;
			synth = def[\synthdef].();  // call mangler creation func
			synth.moveToTail(group);
			def.add(\synth -> synth);
		};
		
		this.resetDefPositions;
	}
	
	drawSignalPath {
		var str;		
		str = "";
		
		definitions.size.do{ |n|
			var next;
			next = definitions.detect{ |def| def[\position] == n };
			str = str + (n+1).asString ++ ": " ++ next[\name].asString + $\n;
		};
		path.string_(str);
	}
	
	drawMangles {
		definitions.do { |defn|
			
			var container,switch,main;
									
			container = SCVLayoutView(w, 120 @ 100)
				.background_( Color(0,0,0.3) )
				;
						
			main = SCStaticText( container, 120 @ 40 )
				.string_( defn[\name] + noteText.(defn[\midinote]) )
				.stringColor_( Color.white )
				.font_( Font("Verdana-Bold", 16) )
				.background_( Color(0.8,0,0) )
				.action_{ |button|
					button.value.switch(	
						0, { this.switchNode(false, note: defn[\midinote]) },
						1, { this.switchNode(true, note: defn[\midinote]) }
					);
				}
				;

			defn.add(\button -> main);
				
			switch = SCButton( container, 120 @ 20 )
				.states_([[ 'ÝÝ', Color.white, Color(0,0.3,0) ]])
				.action_{ |button|
					var current,next;
					
					current = defn[\position];
					
					if(current < (definitions.size - 1), {
						next = definitions.detect{ |def| def[\position] == (current + 1) };
	
						defn.add(\position -> (current + 1));
						next.add(\position -> current);
						defn[\synth].moveAfter(next[\synth]);
						
						this.drawSignalPath;						});
				}
				;
			
			switch = SCButton( container, 120 @ 20 )
				.states_([[ 'ÜÜ', Color.white, Color(0,0.3,0) ]])
				.action_{ |button|
					var current,prev;
					
					current = defn[\position];
					
					if(current > 0, {
						prev = definitions.detect{ |def| def.at(\position) == (current - 1) };
	
						defn.add(\position -> (current - 1));
						prev.add(\position -> current);
						defn[\synth].moveBefore(prev[\synth]);
						
						this.drawSignalPath;						});
				}
				;			
		};
	}
	
	resetDefPositions {
		definitions.do{ |def, n| def.add(\position -> n) }
	}
}
