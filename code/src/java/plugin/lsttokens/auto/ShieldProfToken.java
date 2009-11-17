/*
 * Copyright 2007 (C) Thomas Parker <thpr@users.sourceforge.net>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package plugin.lsttokens.auto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;

import pcgen.cdom.base.CDOMObject;
import pcgen.cdom.base.CDOMReference;
import pcgen.cdom.base.ChooseResultActor;
import pcgen.cdom.base.Constants;
import pcgen.cdom.content.ConditionalChoiceActor;
import pcgen.cdom.enumeration.AssociationListKey;
import pcgen.cdom.enumeration.ListKey;
import pcgen.cdom.helper.ShieldProfProvider;
import pcgen.cdom.helper.SimpleShieldProfProvider;
import pcgen.core.Equipment;
import pcgen.core.Globals;
import pcgen.core.PlayerCharacter;
import pcgen.core.ShieldProf;
import pcgen.core.prereq.Prerequisite;
import pcgen.persistence.PersistenceLayerException;
import pcgen.rules.context.Changes;
import pcgen.rules.context.LoadContext;
import pcgen.rules.persistence.TokenUtilities;
import pcgen.rules.persistence.token.AbstractNonEmptyToken;
import pcgen.rules.persistence.token.CDOMSecondaryParserToken;
import pcgen.rules.persistence.token.ParseResult;

public class ShieldProfToken extends AbstractNonEmptyToken<CDOMObject> implements
		CDOMSecondaryParserToken<CDOMObject>, ChooseResultActor
{

	private static final Class<ShieldProf> SHIELDPROF_CLASS = ShieldProf.class;

	private static final Class<Equipment> EQUIPMENT_CLASS = Equipment.class;

	public String getParentToken()
	{
		return "AUTO";
	}

	@Override
	public String getTokenName()
	{
		return "SHIELDPROF";
	}

	private String getFullName()
	{
		return getParentToken() + ":" + getTokenName();
	}

	@Override
	protected ParseResult parseNonEmptyToken(LoadContext context,
		CDOMObject obj, String value)
	{
		String shieldProf;
		Prerequisite prereq = null; // Do not initialize, null is significant!

		// Note: May contain PRExxx
		if (value.indexOf("[") == -1)
		{
			shieldProf = value;
		}
		else
		{
			int openBracketLoc = value.indexOf("[");
			shieldProf = value.substring(0, openBracketLoc);
			if (!value.endsWith("]"))
			{
				return new ParseResult.Fail("Unresolved Prerequisite in "
						+ getFullName() + " " + value + " in " + getFullName());
			}
			prereq = getPrerequisite(value.substring(openBracketLoc + 1, value
					.length() - 1));
			if (prereq == null)
			{
				return new ParseResult.Fail("Error generating Prerequisite "
						+ prereq + " in " + getFullName());
			}
		}

		ParseResult pr = checkForIllegalSeparator('|', shieldProf);
		if (!pr.passed())
		{
			return pr;
		}

		boolean foundAny = false;
		boolean foundOther = false;

		StringTokenizer tok = new StringTokenizer(shieldProf, Constants.PIPE);

		List<CDOMReference<ShieldProf>> shieldProfs = new ArrayList<CDOMReference<ShieldProf>>();
		List<CDOMReference<Equipment>> equipTypes = new ArrayList<CDOMReference<Equipment>>();

		while (tok.hasMoreTokens())
		{
			String aProf = tok.nextToken();

			if ("%LIST".equals(aProf))
			{
				foundOther = true;
				ChooseResultActor cra;
				if (prereq == null)
				{
					cra = this;
				}
				else
				{
					ConditionalChoiceActor cca = new ConditionalChoiceActor(
							this);
					cca.addPrerequisite(prereq);
					cra = cca;
				}
				context.obj.addToList(obj, ListKey.CHOOSE_ACTOR, cra);
			}
			else if (Constants.LST_ALL.equalsIgnoreCase(aProf))
			{
				foundAny = true;
				shieldProfs.add(context.ref
						.getCDOMAllReference(SHIELDPROF_CLASS));
			}
			else if (aProf.startsWith(Constants.LST_SHIELDTYPE_OLD)
					|| aProf.startsWith(Constants.LST_SHIELDTYPE))
			{
				foundOther = true;
				CDOMReference<Equipment> ref = TokenUtilities.getTypeReference(
						context, EQUIPMENT_CLASS, "SHIELD."
								+ aProf.substring(11));
				if (ref == null)
				{
					return ParseResult.INTERNAL_ERROR;
				}
				equipTypes.add(ref);
			}
			else
			{
				foundOther = true;
				shieldProfs.add(context.ref.getCDOMReference(SHIELDPROF_CLASS,
						aProf));
			}
		}
		if (foundAny && foundOther)
		{
			return new ParseResult.Fail("Non-sensical " + getFullName()
					+ ": Contains ANY and a specific reference: " + value);
		}

		if (!shieldProfs.isEmpty() || !equipTypes.isEmpty())
		{
			ShieldProfProvider pp = new ShieldProfProvider(shieldProfs, equipTypes);
			if (prereq != null)
			{
				pp.addPrerequisite(prereq);
			}
			context.obj.addToList(obj, ListKey.AUTO_SHIELDPROF, pp);
		}

		return ParseResult.SUCCESS;
	}

	public String[] unparse(LoadContext context, CDOMObject obj)
	{
		Changes<ShieldProfProvider> changes = context.obj.getListChanges(obj,
				ListKey.AUTO_SHIELDPROF);
		Changes<ChooseResultActor> listChanges = context.getObjectContext()
				.getListChanges(obj, ListKey.CHOOSE_ACTOR);
		Collection<ShieldProfProvider> added = changes.getAdded();
		Set<String> set = new TreeSet<String>();
		Collection<ChooseResultActor> listAdded = listChanges.getAdded();
		boolean foundAny = false;
		boolean foundOther = false;
		if (listAdded != null && !listAdded.isEmpty())
		{
			for (ChooseResultActor cra : listAdded)
			{
				if (cra.getSource().equals(getTokenName()))
				{
					try
					{
						set.add(cra.getLstFormat());
						foundOther = true;
					}
					catch (PersistenceLayerException e)
					{
						context.addWriteMessage("Error writing Prerequisite: "
								+ e);
						return null;
					}
				}
			}
		}
		if (added != null)
		{
			for (ShieldProfProvider spp : added)
			{
				StringBuilder sb = new StringBuilder();
				sb.append(spp.getLstFormat());
				if (spp.hasPrerequisites())
				{
					sb.append('[');
					sb.append(getPrerequisiteString(context, spp
							.getPrerequisiteList()));
					sb.append(']');
				}
				String ab = sb.toString();
				boolean isUnconditionalAll = Constants.LST_ALL.equals(ab);
				foundAny |= isUnconditionalAll;
				foundOther |= !isUnconditionalAll;
				set.add(ab);
			}
		}
		if (foundAny && foundOther)
		{
			context.addWriteMessage("Non-sensical " + getFullName()
					+ ": Contains ANY and a specific reference: " + set);
			return null;
		}
		if (set.isEmpty())
		{
			//okay
			return null;
		}
		return set.toArray(new String[set.size()]);
	}

	public Class<CDOMObject> getTokenClass()
	{
		return CDOMObject.class;
	}

	public void apply(PlayerCharacter pc, CDOMObject obj, String o)
	{
		ShieldProf wp = Globals.getContext().ref
				.silentlyGetConstructedCDOMObject(SHIELDPROF_CLASS, o);
		if (wp != null)
		{
			pc.addAssoc(obj, AssociationListKey.SHIELDPROF,
					new SimpleShieldProfProvider(wp));
		}
	}

	public void remove(PlayerCharacter pc, CDOMObject obj, String o)
	{
		ShieldProf wp = Globals.getContext().ref
				.silentlyGetConstructedCDOMObject(SHIELDPROF_CLASS, o);
		if (wp != null)
		{
			pc.removeAssoc(obj, AssociationListKey.SHIELDPROF,
					new SimpleShieldProfProvider(wp));
		}
	}

	public String getSource()
	{
		return getTokenName();
	}

	public String getLstFormat()
	{
		return "%LIST";
	}
}
