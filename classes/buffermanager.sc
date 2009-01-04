// TODO
//
// User-friendly functionality EG warning messages if samples not found
// error-handling
// two states on free button, to avoid accidental deletions
//


BufferManager {
	var <w,mappings,<view,server,<name,firstAvailableKitnum,numOpenRows;
	
	*new { |name=(\base),server=(Server.default)|
		^super.new.init(name,server)
	}	
	
	at { |kitnum| 
		var bufhash;
		bufhash = ~buffers[name].detect{ |bufhash| bufhash[\kitnum] == kitnum };
		^(if(bufhash.isNil, { nil }, { bufhash[\buffer] }));
	}
	
	asArray {	
		// return an array of buffers in order of display.
		// Resulting indices will be 0..n, and not necessarily match the kitnum references used with BufferManager#at,
		// which should be used in preference.
		
		// this is currently used for compatibility in the Improbable drum machine - might be of debatable use otherwise.
		^~buffers[name].collect(_[\buffer]);
	}
		
	init { |n,s|
		var topbar;
		# name, server = [n,s];

		// private function: get first available free kit number
		firstAvailableKitnum = {
			(0..255).detect{ |kitnum| 
				~buffers[name].detect{ |bufhash| bufhash[\kitnum] == kitnum }.isNil 
			};
		};
	
		 if(~buffers.isNil, { 
		 	~buffers = IdentityDictionary.new;
		 });

		 if(~buffers[name].isNil, {
		 	~buffers.add(name -> List.new);
		 });
		 
		mappings = IdentityDictionary.new;
		numOpenRows = 0;
	
		w = SCWindow(format("Buffers - %",name.asString), Rect(50,400,430,28), resizable: false);
			
		view = SCVLayoutView(w, Rect(0,0,450,900));
		
		topbar = SCHLayoutView(view, Rect(0,0,450,25));
		SCButton(topbar, Rect(300,0,150,25))
			.states_([[ "New buffer", Color.white, Color(0,0,0.3) ]])
			.action_{ this.addView };
		
		SCButton(topbar, 70 @ 25)
			.states_([
				[ "Load kit", Color.white, Color(0,0.3,0) ]
			])
			.action_{ this.requestKit };
		SCButton(topbar, 70 @ 25)
			.states_([
				[ "Save kit", Color.white, Color(0.3,0,0) ],
				[ "O RLY?", Color.black, Color.yellow ]
			])
			.action_{ |b| b.value.switch(0, { this.savekit }) };
		
		~buffers[name].do { |bufhash|
			this.addView(bufhash);
		};
				
		w.front;
	}
	
	addView { |bufhash|
		var bufview,txt,freebutt,bufn;
		
		bufview = SCHLayoutView(view, w.bounds.width @ 25)
			.background_( Color(0.9,0.9,0.9) );
		
		SCStaticText(bufview, 30 @ 25)
			.string_( "-" );
		
		SCStaticText(bufview, 125 @ 25)
			.string_( "Empty" )
			.background_( Color(0.8,0.8,0.8) );
		
		SCStaticText(bufview, 25 @ 25)
			.string_( '-' )
			.stringColor_( Color.grey );
			
		SCStaticText(bufview, 65 @ 25)
			.string_( '-' )
			.stringColor_( Color.grey );

		SCButton(bufview, 50 @ 25)
			.states_([[ "Load", Color.white, Color(0,0.3,0) ]])
			.action_{ this.bufferDialog(bufview, doneFunc: { |bufhash|
				{ 
					this.removeMapping(bufview);
					this.associateViewWithBuffer(bufview,bufhash) 
				}.defer;
			})};
			
		SCButton(bufview, 60 @ 25)
			.states_([[ "Preview", Color.black, Color.green ]]);
			
		freebutt = SCButton(bufview, 40 @ 25)
			.states_([[ "Free", Color.white, Color(0.3,0,0) ]]);

		this.associateViewWithBuffer(bufview,bufhash);
		numOpenRows = numOpenRows + 1;
		
		this.resizeWindow;
		
		^bufhash;
	}
	
	removeView { |bufview|
		this.removeMapping(bufview);
		mappings.add(bufview -> nil);			     // remove bufview from mappings
		bufview.remove;						  	     // remove bufview from window
		
		numOpenRows = numOpenRows - 1;	
		this.resizeWindow;
	}
	
	removeMapping { |bufview|
		mappings[bufview].free;					     // free buffer
		~buffers[name].removeAllSuchThat{ |bufhash| bufhash[\buffer] == mappings[bufview] };
	}
	
	associateViewWithBuffer{ |view,bufhash|		
		var s1,s2,buf;
		buf = bufhash !? { bufhash[\buffer] };
		
		if(not(buf.isNil), {
			# s1, s2 = buf.duration.asString.split($.);
			view.children[0].string_( bufhash[\kitnum] );
			view.children[1].string_( bufhash[\path].split($/).last[0..17] );
			view.children[2].string_( buf.numChannels.asString + 'ch' );
			view.children[3].string_( format("%.% s", s1, s2[0..3]) );
			view.children[5].action_{ buf.play };
		});

		view.children[6].action_{ this.removeView(view) };
		mappings.add(view -> buf);
		
	}
	
	resizeWindow {
		w.bounds_( Rect( w.bounds.left, w.bounds.top, w.bounds.width, (numOpenRows * 29) + 30 ));
		w.refresh;
	}
	
	bufferDialog { |bufview,doneFunc|
		CocoaDialog.getPaths({ |paths|
			this.readBuffer(paths.first, doneFunc: doneFunc)
		}, maxSize: 1);
	}
	
	readBuffer { |fname,kitnum,doneFunc|
		var hash;
		Buffer.read(server, fname, action: { |buffer|
			hash = IdentityDictionary[
					\buffer -> buffer,
					\path -> fname, // handling path independently from buffer, to make sure we have absolute path at all times
					\kitnum -> (kitnum ?? firstAvailableKitnum.())
			];
			~buffers[name].add(hash);		
			doneFunc.(hash);
		});
	}
	
	// Load a kit: path_prefix is just a convenience method for
	// the presentation of this submission...	
	loadkit { |fname,path_prefix=("")|
		var doc;
		doc = DOMDocument.new(fname);
		
		if(not(doc.isNil), {	
			this.clearAll;
			doc.getDocumentElement.getElementsByTagName("buffer").do { |buffer|
				var path,kitnum;
				path = buffer.getElement("path").getText;
				kitnum = buffer.getElement("kitnum").getText.asInteger;
				this.readBuffer((path_prefix ++ path), kitnum, doneFunc: { |bufhash|
					{ this.addView(bufhash) }.defer;
				});
			};
		});
	}
	
	requestKit {
		CocoaDialog.getPaths({ |paths|
			this.loadkit(paths.first)
		}, maxSize: 1);
	}

	savekit {
		var fname;
		CocoaDialog.savePanel({ |path|
			var doc,root,file;
			doc = DOMDocument.new;
			root = doc.createElement("buffer-kit");
			doc.appendChild(root);
			
			~buffers[name].do { |bufhash, n|
				var buftag,tag;
				buftag = doc.createElement("buffer");
				
				tag = doc.createElement("path");
				tag.appendChild(doc.createTextNode(bufhash[\path]));
				buftag.appendChild(tag);
				
				tag = doc.createElement("kitnum");
				tag.appendChild(doc.createTextNode(bufhash[\kitnum].asString));
				buftag.appendChild(tag);
				
				root.appendChild(buftag);
			};
			
			file = File(path, "w");
			doc.write(file);
			file.close;
			
		}, { 
			// TODO cancel func?
		});
	}

	clearAll {
		mappings.keys.do(this.removeView(_));
	}
}
