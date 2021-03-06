/*
 * Copyright 2011-2013 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui;

import java.math.BigInteger;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence;
import com.google.bitcoin.core.TransactionConfidence.ConfidenceType;
import com.google.bitcoin.core.Wallet;

import de.schildbach.wallet.AddressBookProvider;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.util.CircularProgressView;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class TransactionsListAdapter extends ArrayAdapter<Transaction>
{
	private final Wallet wallet;
	private final ContentResolver resolver;

	private final DateFormat dateFormat;
	private final DateFormat timeFormat;
	private final int colorSignificant;
	private final int colorInsignificant;
	private final int colorError;
	private final int colorCircularBuilding = Color.parseColor("#44ff44");
	private final String textCoinBase;
	private final LayoutInflater inflater;

	private final Map<String, String> labelCache = new HashMap<String, String>();
	private final static String CACHE_NULL_MARKER = "";

	private static final String CONFIDENCE_SYMBOL_NOT_IN_BEST_CHAIN = "!";
	private static final String CONFIDENCE_SYMBOL_DEAD = "\u271D"; // latin cross
	private static final String CONFIDENCE_SYMBOL_UNKNOWN = "?";

	public TransactionsListAdapter(final Context context, final Wallet wallet)
	{
		super(context, 0);

		this.wallet = wallet;
		this.resolver = context.getContentResolver();

		dateFormat = android.text.format.DateFormat.getDateFormat(context);
		timeFormat = android.text.format.DateFormat.getTimeFormat(context);
		final Resources resources = context.getResources();
		colorSignificant = resources.getColor(R.color.fg_significant);
		colorInsignificant = resources.getColor(R.color.fg_insignificant);
		colorError = resources.getColor(R.color.fg_error);
		textCoinBase = context.getString(R.string.wallet_transactions_fragment_coinbase);
		inflater = LayoutInflater.from(context);
	}

	@Override
	public View getView(final int position, View row, final ViewGroup parent)
	{
		if (row == null)
			row = inflater.inflate(R.layout.transaction_row, null);

		final Transaction tx = getItem(position);
		final TransactionConfidence confidence = tx.getConfidence();
		final ConfidenceType confidenceType = confidence.getConfidenceType();

		try
		{
			final BigInteger value = tx.getValue(wallet);
			final boolean sent = value.signum() < 0;

			final CircularProgressView rowConfidenceCircular = (CircularProgressView) row.findViewById(R.id.transaction_row_confidence_circular);
			rowConfidenceCircular.setMaxProgress(Constants.MAX_NUM_CONFIRMATIONS);
			final TextView rowConfidenceTextual = (TextView) row.findViewById(R.id.transaction_row_confidence_textual);

			final int textColor;
			if (confidenceType == ConfidenceType.NOT_SEEN_IN_CHAIN)
			{
				rowConfidenceCircular.setVisibility(View.VISIBLE);
				rowConfidenceTextual.setVisibility(View.GONE);
				textColor = colorInsignificant;

				rowConfidenceCircular.setProgress(1);
				rowConfidenceCircular.setMaxProgress(1);
				rowConfidenceCircular.setSize(confidence.numBroadcastPeers());
				rowConfidenceCircular.setMaxSize(Constants.MAX_CONNECTED_PEERS - 1);
				rowConfidenceCircular.setColors(colorInsignificant, colorInsignificant);
			}
			else if (confidenceType == ConfidenceType.BUILDING)
			{
				rowConfidenceCircular.setVisibility(View.VISIBLE);
				rowConfidenceTextual.setVisibility(View.GONE);
				textColor = colorSignificant;

				rowConfidenceCircular.setProgress(confidence.getDepthInBlocks());
				rowConfidenceCircular.setMaxProgress(tx.isCoinBase() ? Constants.NETWORK_PARAMETERS.getSpendableCoinbaseDepth()
						: Constants.MAX_NUM_CONFIRMATIONS);
				rowConfidenceCircular.setSize(1);
				rowConfidenceCircular.setMaxSize(1);
				rowConfidenceCircular.setColors(colorCircularBuilding, Color.DKGRAY);
			}
			else if (confidenceType == ConfidenceType.NOT_IN_BEST_CHAIN)
			{
				rowConfidenceCircular.setVisibility(View.GONE);
				rowConfidenceTextual.setVisibility(View.VISIBLE);
				textColor = colorSignificant;

				rowConfidenceTextual.setText(CONFIDENCE_SYMBOL_NOT_IN_BEST_CHAIN);
				rowConfidenceTextual.setTextColor(Color.RED);
			}
			else if (confidenceType == ConfidenceType.DEAD)
			{
				rowConfidenceCircular.setVisibility(View.GONE);
				rowConfidenceTextual.setVisibility(View.VISIBLE);
				textColor = Color.RED;

				rowConfidenceTextual.setText(CONFIDENCE_SYMBOL_DEAD);
				rowConfidenceTextual.setTextColor(Color.RED);
			}
			else
			{
				rowConfidenceCircular.setVisibility(View.GONE);
				rowConfidenceTextual.setVisibility(View.VISIBLE);
				textColor = colorInsignificant;

				rowConfidenceTextual.setText(CONFIDENCE_SYMBOL_UNKNOWN);
				rowConfidenceTextual.setTextColor(colorInsignificant);
			}

			final TextView rowTime = (TextView) row.findViewById(R.id.transaction_row_time);
			final Date time = tx.getUpdateTime();
			rowTime.setText(time != null ? (DateUtils.isToday(time.getTime()) ? timeFormat.format(time) : dateFormat.format(time)) : null);
			rowTime.setTextColor(textColor);

			final TextView rowFromTo = (TextView) row.findViewById(R.id.transaction_row_fromto);
			rowFromTo.setText(sent ? R.string.symbol_to : R.string.symbol_from);
			rowFromTo.setTextColor(textColor);

			final TextView rowAddress = (TextView) row.findViewById(R.id.transaction_row_address);
			final Address address = sent ? WalletUtils.getToAddress(tx) : WalletUtils.getFromAddress(tx);
			final String label;
			if (tx.isCoinBase())
				label = textCoinBase;
			else if (address != null)
				label = resolveLabel(address.toString());
			else
				label = "?";
			rowAddress.setTextColor(textColor);
			rowAddress.setText(label != null ? label : address.toString());
			rowAddress.setTypeface(label != null ? Typeface.DEFAULT : Typeface.MONOSPACE);

			final CurrencyAmountView rowValue = (CurrencyAmountView) row.findViewById(R.id.transaction_row_value);
			rowValue.setCurrencyCode(null);
			rowValue.setAmountSigned(true);
			rowValue.setTextColor(textColor);
			rowValue.setAmount(value);

			final View rowExtend = row.findViewById(R.id.transaction_row_extend);
			final TextView rowMessage = (TextView) row.findViewById(R.id.transaction_row_message);
			final boolean isLocked = tx.getLockTime() > 0;
			rowExtend.setVisibility(View.GONE);
			if (!sent && confidenceType == ConfidenceType.NOT_SEEN_IN_CHAIN && isLocked)
			{
				rowExtend.setVisibility(View.VISIBLE);
				rowMessage.setText(R.string.transaction_row_message_unconfirmed_locked);
				rowMessage.setTextColor(colorError);
			}
			else if (!sent && confidenceType == ConfidenceType.NOT_SEEN_IN_CHAIN && !isLocked)
			{
				rowExtend.setVisibility(View.VISIBLE);
				rowMessage.setText(R.string.transaction_row_message_unconfirmed_unlocked);
				rowMessage.setTextColor(colorInsignificant);
			}
			else if (!sent && confidenceType == ConfidenceType.NOT_IN_BEST_CHAIN)
			{
				rowExtend.setVisibility(View.VISIBLE);
				rowMessage.setText(R.string.transaction_row_message_unconfirmed_unlocked);
				rowMessage.setTextColor(colorError);
			}
			else if (!sent && confidenceType == ConfidenceType.DEAD)
			{
				rowExtend.setVisibility(View.VISIBLE);
				rowMessage.setText(R.string.transaction_row_message_dead);
				rowMessage.setTextColor(colorError);
			}

			return row;
		}
		catch (final ScriptException x)
		{
			throw new RuntimeException(x);
		}
	}

	private String resolveLabel(final String address)
	{
		final String cachedLabel = labelCache.get(address);
		if (cachedLabel == null)
		{
			final String label = AddressBookProvider.resolveLabel(resolver, address);
			if (label != null)
				labelCache.put(address, label);
			else
				labelCache.put(address, CACHE_NULL_MARKER);
			return label;
		}
		else
		{
			return cachedLabel != CACHE_NULL_MARKER ? cachedLabel : null;
		}
	}

	public void clearLabelCache()
	{
		labelCache.clear();

		notifyDataSetChanged();
	}
}
