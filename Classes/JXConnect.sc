JXConnect {
	*new {|lhs, rhs ...extras|
		JXConnect.prChecks(lhs, rhs, *extras);
		JXResourceConnectionFunctionsStore.combined(lhs, rhs, *extras);
	}
	*makeConnectionOnly {|lhs, rhs ...extras|
		JXConnect.prChecks(lhs, rhs, *extras);
		JXResourceConnectionFunctionsStore.makeConnection(lhs, rhs, *extras);
	}
	*postConnectionCleaup {|lhs, rhs ...extras|
		JXConnect.prChecks(lhs, rhs, *extras);
		JXResourceConnectionFunctionsStore.postConnectionCleaup(lhs, rhs, *extras);
	}
	*prChecks {
		|lhs, rhs ...extras|
		JXTypeCheck(thisMethod, lhs -> JXOwnedResourcePair, rhs -> JXOwnedResourcePair);
		JXAssert(thisMethod, lhs.owner != rhs.owner, 'Cannot connect between the same object');
	}
}

// Used to expand the associations before calling JXConnect.
// Used internally.
JXResourceConnectionManager {
	*prRouter { |assoc, interface|
		JXTypeCheck(thisMethod, interface -> JXResourceConnectionManagerInterface);
		case
		{JXTypeQuery(assoc.key -> SequenceableCollection)}
		{assoc.key.do({ |k|
			JXResourceConnectionManager.prRouter(k -> assoc.value, interface)
		})}

		{JXTypeQuery(assoc.value -> SequenceableCollection)}
		{assoc.value.do({ |v|
			JXResourceConnectionManager.prRouter(assoc.key -> v, interface)
		})}

		{JXTypeQuery(assoc.key -> [Symbol, Class])}
		{JXResourceConnectionManager.prRouter(interface.lookup(assoc.key) -> assoc.value, interface) }

		{JXTypeQuery(assoc.value -> [Symbol, Class])}
		{JXResourceConnectionManager.prRouter(assoc.key -> interface.lookup(assoc.value), interface) }

		{JXTypeQuery(assoc.key -> Function)}
		{JXResourceConnectionManager.prRouter(interface.evalOnObject(assoc.key) -> assoc.value, interface) }

		{JXTypeQuery(assoc.value -> Function)}
		{JXResourceConnectionManager.prRouter(assoc.key -> interface.evalOnObject(assoc.value), interface) }


		{JXTypeQuery(assoc.key -> Association)}
		{JXResourceConnectionManager.prRouter(assoc.key.key -> ( assoc.key.value -> assoc.value), interface) }


		{
			var array = JXAssoc2Array(assoc);
			interface.performAction(array.first, array.last, *array[1..array.size - 2]);
		}

	}
}

// the interface for JXResourceConnectionManager
JXResourceConnectionManagerInterface {
	var symbolLookUpFunc, metaObjectLookUpFunc, activeObject, action;
	*new {|symbolLookUpFunc, metaObjectLookUpFunc, activeObject, action|
		JXTypeCheck(thisMethod,
			symbolLookUpFunc -> Function, metaObjectLookUpFunc -> Function,
			action -> Function,	activeObject -> Object
		);
		^super.newCopyArgs(symbolLookUpFunc, metaObjectLookUpFunc, activeObject, action)
	}
	lookup { |t|
		var check = JXTypeCheck(thisMethod,  t -> [Symbol, Class]);
		var ret = case
		{JXTypeQuery(t -> Symbol)}  {symbolLookUpFunc.(t)}
		{JXTypeQuery(t -> Class)}   {metaObjectLookUpFunc.(t)};

		JXTypeCheck(thisMethod,  ret -> [JXOwnedResourcePair, SequenceableCollection]);
		ret.asArray.do({|r| JXTypeCheck(thisMethod,  r -> JXOwnedResourcePair) });

		^ret;
	}
	evalOnObject { |v|
		JXTypeCheck(thisMethod, v -> Function);
		^v.(activeObject)
	}
	performAction { |left, right| ^action.(left, right) }
}





// Used to store the connection functions
JXResourceConnectionFunctionsStore {
	// this class implements a form of multiple dispatch
	// where the types as Associations are provided as the key, and the function the value
	// e.g. connecting from A <:JXResource to B <:JXResource with function foo: store[(A -> B).asSymbol] = foo;
	classvar store;
	*initClass { store = () }
	*makeConnection { |lhsobj, rhsobj ...extras|
		var key = JXResourceConnectionFunctionsStore.prGetKeyFromOwnedPair(lhsobj, rhsobj);
		store[key].makeConnectionOnly(lhsobj, rhsobj, *extras);
	}
	*postConnectionCleaup { |lhsobj, rhsobj ...extras|
		var key = JXResourceConnectionFunctionsStore.prGetKeyFromOwnedPair(lhsobj, rhsobj);
		store[key].postConnectionCleanup(lhsobj, rhsobj, *extras);
	}
	*combined { |lhsobj, rhsobj ...extras|
		var key = JXResourceConnectionFunctionsStore.prGetKeyFromOwnedPair(lhsobj, rhsobj);
		store[key].makeConnectionOnly(lhsobj, rhsobj, *extras);
		store[key].postConnectionCleanup(lhsobj, rhsobj, *extras);
	}
	*prGetKeyFromOwnedPair { |lhsobj, rhsobj|
		var checkr = JXTypeCheck(thisMethod, lhsobj -> JXOwnedResourcePair, rhsobj -> JXOwnedResourcePair);
		var key = (lhsobj.resource.class -> rhsobj.resource.class).asSymbol;
		JXAssert(thisMethod, store.includesKey(key),
			"Cannot connect the resources, % to % because there is no function avaible, "
			"run \"JXResourceConnectionFunctionsStore.printAllConnectionTypes\" to see a complete list"
			.format(lhsobj.resource.class, rhsobj.resource.class));
		^key
	}
	*printAllConnectionTypes {
		"it is possible to connect:".postln;
		store.keys.asArray.do{|d| ("\t" ++ d.asString).postln; }
	}
	*add { |lhs, rhs, func|
		var checka = JXTypeCheck(thisMethod, lhs -> Class, rhs -> Class);
		var checkb = JXInheritsCheck(lhs -> JXResource, rhs -> JXResource);
		var key = (lhs -> rhs).asSymbol;
		if( store.includesKey(key),
			{^Error("key % already set".format(lhs -> rhs)).throw});
		store[key] = func;
	}
}

// inherited when defining how functions should be connected
JXResourceConnectionFunctionBase {
	*register { |lhs, rhs, self|
		Class.initClassTree(JXResourceConnectionFunctionsStore);
		JXTypeCheck(thisMethod, lhs -> Class, rhs -> Class);
		JXInheritsCheck(lhs -> JXResource, rhs -> JXResource);
		JXResourceConnectionFunctionsStore.add(lhs, rhs, self);
	}
	*makeConnectionOnly { |lhs, rhs ...extras| ^this.subclassResponsibility(thisMethod) }
	*postConnectionCleanup { |lhs, rhs ...extras| ^this.subclassResponsibility(thisMethod) }
}







