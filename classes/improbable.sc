// TODO 
// graphical representation of playback on patterns grid
// better interface to arrange song
// UNDO func...? (esp. paste)

Improbable {
	var w, w2;
	var songList,songGrid;
	var patterns,npatterns,currentPattern;
	var patternGrid,controlGrid,channelGrid,nchannels,playButton;
	var tempo,bufman,patternName,rout,clock,<>outbus,<tempoBus,<group;
	var <pulsebar,<pulseQ, <pulseT,flash;
	var copiedPattern;

	*new { |bufman,out=0|
		^super.new.init(bufman,out);
	}
	
	init { |b,i|
		
		# bufman, outbus = [ b, i ];
		
		npatterns = 16;
		nchannels = 8;
		currentPattern = 0;
		songList = List.new;
		group = Group.new;
		
		clock = TempoClock(120/60);
		
		// three control buses for external synching purposes - triggered at each bar and 1/4 and 1/16th note
		# pulsebar, pulseQ, pulseT = { Bus.control } ! 3;
		flash = { |bus| { bus.set(1); 0.05.wait; bus.set(0); }.fork };       // TODO better way to flash a trigger from client?  InTrig.kr

		// going to send the tempo on a control bus too
		tempoBus = Bus.control.set(clock.tempo * 60);

		patterns = Array.newClear(npatterns);
		npatterns.do { |n|
			patterns[n] = IdentityDictionary[
				\name -> ('unnamed' ++ (n+1).asString),
				\probabilities -> ( 0 ! 16 ! nchannels ),
				\songProbabilities -> ( 0 ! 32 ),
				\velocities -> ( 1 ! 16 ! nchannels )      // for future use
			]
		}; 
		
		w = SCWindow("Mission Improbable", Rect(500,300,465,520), resizable: false);

		this.initGUI;
		this.resetCurrentPattern;
		
		w.front;
	}
	
	tempo_ { |newTempo|
		clock.tempo = (newTempo / 60);
		tempoBus.set(newTempo);
		^newTempo;
	}
	
	// play the whole song, repeatedly
	playSong {
		if(rout.isPlaying, { rout.stop });
		
		rout = Routine {
			inf.do { |bar|
				if(bar > 31, { bar = 0 };
				
				var players,weights;
				players = List.new;

				npatterns.do { |n|
					if(patterns[n][\songProbabilities][bar] > 0, { players.add(n) });
				};
				
				if(players.size > 0, {	
					
					// collect the probabilities
					weights = players.collect{ |patn| patterns[patn][\songProbabilities][bar] }.normalizeSum;
					
					// and play a random pattern
					this.playPattern(players.wchoose(weights));
					
					4.wait
				});
			}
		};
		
		clock.schedAbs(clock.nextBar, rout);
	}
	
	// play a pattern, once
	playPattern { |pattern|	
		Routine {	

			16.do { |n|

				// flash triggers for external sync
				flash.(pulseT);
				if(n % 4 == 0, { flash.(pulseQ) });
				if(n % 16 == 0, { flash.(pulsebar) });

				nchannels.do { |chan|
					var prob;
					prob = patterns[pattern][\probabilities][chan][n];
					if(prob.coin, {
						Synth.head(group, \sampler, [\bufnum, bufman.asArray[chan].bufnum, \mul, prob, \out, outbus]);
					});
				};

				0.25.wait;
			}
		}.play(clock) 		// needs to be quant'd to a beat? seems to work ok atm
	}

	// loop the current pattern
	loopPattern { |pattern|		
		if(rout.isPlaying, { rout.stop });
		
		rout = Routine {
			inf.do {
				this.playPattern(pattern);
				4.wait
			}
		};
		
		clock.schedAbs(clock.nextBar, rout);
	}
	
	// redraw all GUI components
	redraw {
		var hstrips;
		
		rout.stop;
		playButton.value = 0;
		
		hstrips = channelGrid.children.select{ |child| child.class == SCHLayoutView };
		patterns[currentPattern][\probabilities].do { |hits,chann|
			hits.do { |probability,hitn|
				hstrips[chann].children[hitn].value = probability;
			}
		};
		
		patternName.value = patterns[currentPattern][\name];
		this.resetCurrentPattern;
	}
	
	// load song from an XML file
	loadsong { |fname|
		var doc,patternList;
		doc = DOMDocument.new(fname);
		patternList = doc.getDocumentElement.getElementsByTagName("pattern");
		patternList.do { |pattern|
			var channelList,songProbList,patn;
			patn = pattern.getAttribute("number").asInteger;
			channelList = pattern.getElementsByTagName("channel");
			channelList.do { |chan|
				var chann,hitList;
				chann = chan.getAttribute("number").asInteger;
				hitList = chan.getElementsByTagName("hit");
				16.do { |hitn|
					var hit;
					// perhaps use pop/shift here, more efficient
					hit = hitList.detect{ |h| h.getAttribute("number").asInteger == hitn };
					hit !? {
						patterns[patn][\probabilities][chann][hitn] = hit.getAttribute("probability").asFloat;
					};
				};
			};
			
			songProbList = pattern.getElementsByTagName("songprobability");
			patterns[patn][\songProbabilities] = songProbList.collect(_.getText.asFloat);
		};
		
		currentPattern = doc.getDocumentElement.getElement("currentPattern").getText.asInteger;
		this.redraw;
	}
	
	// save song to an XML file
	// TODO some of the XML is a tad messy, could tidy it up..
	// FIX saving pattern names is not working at the moment...
	write { |fname|
		var doc,e,root,tag,file;
		doc = DOMDocument.new;
		root = doc.createElement("song");
		doc.appendChild(root);
		
		tag = doc.createElement("currentPattern");
		tag.appendChild(doc.createTextNode(currentPattern.asString));
		root.appendChild(tag);
		
		patterns.do { |pattern,n|
			var tag;
			tag = doc.createElement("pattern");
			tag.setAttribute("name", pattern[\name]);
			tag.setAttribute("number", n.asString);
			nchannels.do { |chan|
				var chanTag;
				chanTag = doc.createElement("channel");
				chanTag.setAttribute("number", chan.asString);
				
				16.do { |hit|
					var hitTag,prob;
					prob = pattern[\probabilities][chan][hit];
					if(prob > 0, {
						hitTag = doc.createElement("hit");
						hitTag.setAttribute("probability", prob.asString);
						hitTag.setAttribute("velocity", "1.0");    // for future use
						hitTag.setAttribute("number", hit.asString);
						chanTag.appendChild(hitTag); 
					});
				};
				tag.appendChild(chanTag);
			};
			pattern[\songProbabilities].do { |prob,m|
				var songTag;
				songTag = doc.createElement("songprobability");
				songTag.setAttribute("bar", m.asString);
				songTag.appendChild(doc.createTextNode(prob.asString));
				tag.appendChild(songTag);
			};
			root.appendChild(tag);
		};
		
		file=File(fname,"w");
		doc.write(file);
		file.close;
	}
	
	requestLoadFile { |doneFunc|
		CocoaDialog.getPaths({ |paths|
			var filename;
			filename = paths.first;
			doneFunc.value(filename);
		},
		maxSize: 1);
	}
	
	requestSaveFile { |doneFunc|
		CocoaDialog.savePanel({ |path| doneFunc.value(path) });
	}
	

	resetCurrentPattern {
		patternGrid.children.do{ |button,i|
			button.value = if(i != currentPattern, 0, 1);
		};
		songGrid.value_( patterns[currentPattern][\songProbabilities] );
	}
	
	
	// set up GUI on open
	initGUI {
		patternGrid = SCCompositeView(w, Rect( 0,0,196,52))
			.background_( Color(0.7,0.7,0.7) )
			;
		patternGrid.decorator = FlowLayout( patternGrid.bounds );
		
		npatterns.do { |n|
			SCButton(patternGrid, 20 @ 20)
				.states_([
					[ (n+1).asString, Color.white, Color.red ],
					[ (n+1).asString, Color.black, Color.yellow ]
				])
				.action_{ |button|
					if( n == currentPattern, {
						button.value = 1
					}, {
						currentPattern = n;
						this.resetCurrentPattern;
						this.redraw;
					});
				}
				;
		};
				
		controlGrid = SCCompositeView(w, Rect(202,0,255,52))
			.background_( Color( 0.7,0.7,0.7 ) )
			;
		controlGrid.decorator = FlowLayout( controlGrid.bounds );
		
		SCStaticText(controlGrid,Rect(92,0,60,20))
			.string_( "Tempo" )
			.background_( Color( 0.65,0.65,0.65 ))
			;
		tempo = SCNumberBox(controlGrid, Rect(154,0,30,20))
			.value_( 120 )
			.action_{ |box| this.tempo_( box.value ) }
			;	

			
		SCStaticText(controlGrid, Rect(306, 0, 25, 20))
			.string_("out")
			;
		SCNumberBox(controlGrid, Rect(333,0,35,20))
			.value_( outbus )
			.focusColor_( Color.red )
			.action_{ |box| outbus = box.value }
			;
			
			
		SCButton(controlGrid, Rect(226,0,78,20))
			.states_([[ "Play Song", Color.black, Color.green ]])
			.action_{ this.playSong }
			;	
			
		controlGrid.decorator.nextLine;	

			
		SCButton(controlGrid, 80 @ 20)
			.states_([[ "Load song", Color.white, Color(0,0.3,0) ]])
			.action_{ this.requestLoadFile(doneFunc: { |filename|
					this.loadsong(filename);
				})
			}
			;
		SCButton(controlGrid, 80 @ 20)
			.states_([
				[ "Save song", Color.white, Color(0.3,0,0) ],
				[ "ORLY", Color.black, Color.yellow ]
			])
			.action_{ |button|
				if(button.value == 0, {
					this.requestSaveFile { |fname| 
						this.write(fname)
					};
				});
			}
			;
						
		channelGrid = SCCompositeView(w, Rect(0,56,457,300))
			.background_( Color( 0.7,0.7,0.7 ))
			;
		channelGrid.decorator = FlowLayout( channelGrid.bounds );
		SCStaticText( channelGrid, Rect(0,0,100,20) )
			.background_( Color( 0.65,0.65,0.65 ) )
			.string_( "Pattern name" )
			;
		patternName = SCTextField( channelGrid, Rect( 102,0,90,20 ))
			.value_( patterns[currentPattern][\name] )
			.action_{ |b| patterns[currentPattern][\name] = b.value }
			;
			
		playButton = SCButton( channelGrid, Rect( 196,0,50,20 ))
			.states_([
				[ "Play", Color.black, Color.green ],
				[ "Stop", Color.white, Color.red ]
			])
			.action_{ |me|
				me.value.switch(
					1, { this.loopPattern( currentPattern ) },
					0, { rout.stop }
				);
			}
			;
			
		SCButton( channelGrid, Rect( 248,0,50,20 ))
			.states_([[ "Copy", Color.black, Color.grey ]])
			.action_{ 
				copiedPattern = IdentityDictionary[
					\velocities -> patterns[currentPattern][\velocities].deepCopy,
					\probabilities -> patterns[currentPattern][\probabilities].deepCopy
				];
			}
			;
			
		SCButton( channelGrid, Rect( 248,0,50,20 ))
			.states_([[ "Paste", Color.black, Color.grey ]])
			.action_{ 
				copiedPattern !? {
					patterns[currentPattern][\velocities] = copiedPattern[\velocities];
					patterns[currentPattern][\probabilities] = copiedPattern[\probabilities];
					this.redraw;
				};
			}
			;
		
		nchannels.do { |chan|
			var layout;
			layout = SCHLayoutView( channelGrid, Rect(0, 24+(chan*32), 450, 30) )
				.background_( Color( 0.65,0.65,0.65 ) )
				;
				
			16.do { |hit|
				var slider;
				slider = SCSlider( layout, 24 @ 20 )
					.action_{ |slid| patterns[currentPattern][\probabilities][chan][hit] = slid.value }
					;
				if(hit % 4 == 0, { slider.background_( Color( 0.75,0.75,0.75 ) ) });
			}
		};
		
		songGrid = SCMultiSliderView(w, Rect(0,360,457,140))
			.action_{ |slid| patterns[currentPattern][\songProbabilities] = slid.value }
			.indexThumbSize_( 13 )
			;
	}
		
}