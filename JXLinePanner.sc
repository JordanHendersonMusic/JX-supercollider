// mono to line
JXLinePanner {
	*ar {|numChans, sig, pan|
		^JXLinePanner.prUgenMoreThanStereo(\ar, numChans, sig, pan)
	}

	*kr {|numChans, sig, pan|
		^JXLinePanner.prUgenMoreThanStereo(\kr, numChans, sig, pan)
	}

	*chanRouter {|rate, numChans, sig, pos|
		case
		{ numChans == 0 } { ^DC.perform(rate, 0) }
		{ numChans == 1 } { ^Mix.perform(rate, sig) }
		{ numChans == 2 } { ^Pan2.perform(rate, sig, pos) }
		{ ^JXLinePanner.prUgenMoreThanStereo(rate, numChans, sig, pos) }
	}

	*prUgenMoreThanStereo {|rate, numChans, sig, panRaw|
		var pan = panRaw.clip(-1, 0.999999999999999).lag(0.05);
		var splits = numChans - 1;
		var splitWidth = 2 / splits;
		var splitPoints = splits.collect{|s| [-1 + (2*s / splits), -1 + (2*(s+1) / splits) ] };
		var many_chans = splitPoints.collect{|sp|
			var in_split = (sp[0] <= pan).asInteger * (pan < sp[1]).asInteger;
			var pos = (pan - sp[0]) / splitWidth;
			Pan2.perform(rate, sig, (pos * 2) - 1) * in_split;
		};
		var chans = many_chans.flat;
		^numChans.collect{|i|
			switch(i)
			{ 0 }            { chans[0] }
			{ numChans - 1 } { chans[(i*2) - 1] }
			/* { else } */   { chans[i*2] + chans[(i*2) - 1] }
		}
	}
}
// many to line
JXLineSpreader {
	*ar { |numChans, sig|
		^JXLineSpreader.prUgen(\ar, numChans, sig)
	}
	*kr { |numChans, sig|
		^JXLineSpreader.prUgen(\kr, numChans, sig)
	}
	*prUgen {|rate, numChans, sig|
		^Mix.perform(rate,
			sig.collect({ |s, i|
				var p = (i / (sig.size - 1)) * 2 - 1;
				JXLinePanner.perform(rate, numChans, s, p)
			})
		)
	}
}

// line to a point source - sum all channels and play on one.
JXLine2Point {
	*ar { |sig, pan, focus|
		^JXLine2Point.prUgen(\ar, sig, pan, focus)
	}
	*kr { |sig, pan, focus|
		^JXLine2Point.prUgen(\kr, sig, pan, focus)
	}
	*prUgen {|rate, sig, pan, focus|
		^Mix.perform(rate,
			sig.collect{|s, i|
				var defaultPos = (i / (sig.size - 1)) * 2 - 1;
				var pos = defaultPos.blend(pan, focus.clip(0, 1));
				JXLinePanner.perform(rate, sig.size, s, pos.clip(-1, 1));
			}
		)
	}
}
// line to point source with fade - mono source
JXLinePushAway {
	*ar { |sig, pan, distance|
		^JXLinePushAway.prUgen(\ar, sig, pan, distance)
	}
	*kr { |sig, pan, distance|
		^JXLinePushAway.prUgen(\kr, sig, pan, distance)
	}
	*prUgen {|rate, sig, pan, distance|
		var focus = JXLine2Point.perform(rate, sig, pan, distance);
		var gain = (1 - distance).pow(2).lag(0.1);
		var r = focus * gain;
		^LPF.ar(r, distance.clip(0,1).linexp(0, 1, 19000, 2000))
	}
}

// spread channels across line
JXLineBlur {
	*ar { |sig, blur|
		^JXLineBlur.prUgen(\ar, sig, blur)
	}
	*kr { |sig, blur|
		^JXLineBlur.prUgen(\kr, sig, blur)
	}
	*prUgen {|rate, sig, blur|
		var dis = blur * sig.size;
		^sig.collect{|s, i|
			var weights = sig.size.collect{|n|
				var w = ((1 - ((n - i).abs)) + dis).clip(0,1);
				w = sin( w * (pi/2) );
				w * dis.linlin(0, sig.size, 1, sig.size).reciprocal;
			};
			Mix.ar( sig * weights );
		};
	}
}

// get mono from line
JXLineSample {
	*ar{|sig, pos=0, spread=0| ^JXLineSample.prUgen(sig, pos, spread)	}
	*kr{|sig, pos=0, spread=0| ^JXLineSample.prUgen(sig, pos, spread)	}

	*prUgen { |sig, pos, sprd|
		var spread = sprd;
		var posN = ((pos + 1 ) / 2) * (sig.size - 1);
		var r = sig.size.collect{|n|
			var w = ((1 - (n - posN).abs) + (spread*sig.size)).clip(0,1) ;
			w = sin(w * (pi/2));
		};
		var weights = r / r.sum;
		^(sig * weights).sum;
	}
}

// focus in on a part of the line - produces a uniform result.
JXLineFocus {
	*ar{|sig, pos, focus, spread|
		^JXLineFocus.prUgen(\ar, sig, pos, focus, spread)
	}
	*kr{|sig, pos, focus, spread|
		^JXLineFocus.prUgen(\kr, sig, pos, focus, spread)
	}
	*prUgen { |rate, sig, pos, focus, spread|
		^sig.size.collect{|n|
			var thisPos = (n / (sig.size-1)) * 2 - 1;
			JXLineSample.perform(rate, sig, thisPos.blend(pos, focus), spread)
		};
	}
}

