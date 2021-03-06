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

import java.math.BigDecimal;
import java.math.BigInteger;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.bitcoin.core.AbstractWalletEventListener;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.Wallet.BalanceType;
import com.google.bitcoin.core.WalletEventListener;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.ExchangeRatesProvider;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class WalletBalanceFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor>
{
	private WalletApplication application;
	private Wallet wallet;
	private SharedPreferences prefs;
	private final Handler handler = new Handler();

	private CurrencyAmountView viewBalance;
	private TextView viewBalanceLocal;

	private final WalletEventListener walletEventListener = new AbstractWalletEventListener()
	{
		@Override
		public void onTransactionConfidenceChanged(final Wallet wallet, final Transaction tx)
		{
			// swallow
		}

		@Override
		public void onChange()
		{
			getActivity().runOnUiThread(new Runnable()
			{
				public void run()
				{
					updateView();
				}
			});
		}
	};

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		final Activity activity = getActivity();
		application = (WalletApplication) activity.getApplication();
		prefs = PreferenceManager.getDefaultSharedPreferences(activity);

		wallet = application.getWallet();
		wallet.addEventListener(walletEventListener);
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);

		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.wallet_balance_fragment, container, false);
		viewBalance = (CurrencyAmountView) view.findViewById(R.id.wallet_balance);
		viewBalanceLocal = (TextView) view.findViewById(R.id.wallet_balance_local);

		if ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) < Configuration.SCREENLAYOUT_SIZE_LARGE)
		{
			view.setOnClickListener(new OnClickListener()
			{
				public void onClick(final View v)
				{
					startActivity(new Intent(getActivity(), ExchangeRatesActivity.class));
				}
			});
		}

		return view;
	}

	@Override
	public void onResume()
	{
		super.onResume();

		updateView();
	}

	@Override
	public void onDestroy()
	{
		wallet.removeEventListener(walletEventListener);

		super.onDestroy();
	}

	public void updateView()
	{
		viewBalance.setAmount(wallet.getBalance(BalanceType.ESTIMATED));

		getLoaderManager().restartLoader(0, null, this);
	}

	private Runnable resetColorRunnable = new Runnable()
	{
		public void run()
		{
			viewBalanceLocal.setTextColor(Color.parseColor("#888888"));
		}
	};

	public void flashLocal()
	{
		viewBalanceLocal.setTextColor(Color.parseColor("#cc5500"));
		handler.removeCallbacks(resetColorRunnable);
		handler.postDelayed(resetColorRunnable, 500);
	}

	public Loader<Cursor> onCreateLoader(final int id, final Bundle args)
	{
		final String exchangeCurrency = prefs.getString(Constants.PREFS_KEY_EXCHANGE_CURRENCY, Constants.DEFAULT_EXCHANGE_CURRENCY);
		return new CursorLoader(getActivity(), ExchangeRatesProvider.CONTENT_URI, null, ExchangeRatesProvider.KEY_CURRENCY_CODE,
				new String[] { exchangeCurrency }, null);
	}

	public void onLoadFinished(final Loader<Cursor> loader, final Cursor data)
	{
		if (data != null)
		{
			data.moveToFirst();
			final Double exchangeRate = data.getDouble(data.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_EXCHANGE_RATE));

			viewBalanceLocal.setVisibility(View.GONE);
			if (application.getWallet().getBalance(BalanceType.ESTIMATED).signum() > 0 && exchangeRate != null)
			{
				final String exchangeCurrency = prefs.getString(Constants.PREFS_KEY_EXCHANGE_CURRENCY, Constants.DEFAULT_EXCHANGE_CURRENCY);
				final BigInteger balance = wallet.getBalance(BalanceType.ESTIMATED);
				final BigDecimal bdExchangeRate = new BigDecimal(exchangeRate);
				final BigInteger localValue = WalletUtils.localValue(balance, bdExchangeRate);
				viewBalanceLocal.setVisibility(View.VISIBLE);
				viewBalanceLocal.setText(getString(R.string.wallet_balance_fragment_local_value, exchangeCurrency,
						WalletUtils.formatValue(localValue)));
				if (Constants.TEST)
					viewBalanceLocal.setPaintFlags(viewBalanceLocal.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
			}
		}
	}

	public void onLoaderReset(final Loader<Cursor> loader)
	{
	}
}
