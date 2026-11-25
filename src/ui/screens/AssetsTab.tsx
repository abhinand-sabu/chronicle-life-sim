import React, { useState } from 'react';
import { Building2, CreditCard, Home, Lock, TrendingUp } from 'lucide-react';
import { calculateNetWorth, getAgeYears, getCountry } from '../../engine';
import type { EraPack, GameState, PlayerAction } from '../../engine';
import { formatMoney } from '../../store/saveStore';

interface AssetsTabProps {
  state: GameState;
  pack: EraPack;
  onAction: (action: PlayerAction) => void;
}

const NetWorthChart: React.FC<{ history: Array<{ ageMonths: number; netWorth: number }> }> = ({
  history,
}) => {
  if (history.length < 2) {
    return (
      <div style={{ fontSize: 11, color: 'var(--text-muted)', padding: '12px 0', textAlign: 'center' }}>
        Advance a few turns to plot your Net Worth trajectory.
      </div>
    );
  }

  const values = history.map((h) => h.netWorth);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = Math.max(1, max - min);
  const width = 320;
  const height = 74;

  const points = history
    .map((h, i) => {
      const x = (i / (history.length - 1)) * width;
      const y = height - 6 - ((h.netWorth - min) / range) * (height - 12);
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(' ');

  const isPositive = (values[values.length - 1] ?? 0) >= (values[0] ?? 0);
  const stroke = isPositive ? '#10B981' : '#F43F5E';

  return (
    <svg viewBox={`0 0 ${width} ${height}`} style={{ width: '100%', height: 74, overflow: 'visible' }}>
      <polyline
        fill="none"
        stroke={stroke}
        strokeWidth="2.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        points={points}
      />
    </svg>
  );
};

export const AssetsTab: React.FC<AssetsTabProps> = ({ state, pack, onAction }) => {
  const [investAmount, setInvestAmount] = useState(1000);
  const country = getCountry(pack, state.config.countryId);
  const ageYears = getAgeYears(state.player.ageMonths);
  const isLegalAdult = ageYears >= country.legalAdultAge;
  const { finance, education } = state.player;
  const netWorth = calculateNetWorth(state, pack);
  const creditPct = Math.round(((finance.creditScore - 300) / 550) * 100);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {/* Net Worth Hero */}
      <div className="card">
        <div className="section-header" style={{ marginBottom: 4 }}>
          <span className="section-title">Net Worth</span>
          <span className="chip chip-emerald tabular">
            Peak {formatMoney(state.peakNetWorth, country)}
          </span>
        </div>
        <div
          className="tabular"
          style={{
            fontFamily: 'var(--font-display)',
            fontSize: 28,
            fontWeight: 800,
            color: netWorth >= 0 ? 'var(--accent-emerald)' : 'var(--accent-rose)',
            marginBottom: 8,
          }}
        >
          {formatMoney(netWorth, country)}
        </div>

        <NetWorthChart history={finance.netWorthHistory} />

        <div
          className="tabular"
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(3, 1fr)',
            gap: 6,
            marginTop: 10,
            paddingTop: 10,
            borderTop: '1px solid var(--border-subtle)',
            fontSize: 11,
          }}
        >
          <div>
            <div style={{ color: 'var(--text-muted)' }}>Last Income</div>
            <strong style={{ color: '#34D399' }}>+{formatMoney(finance.lastTurnIncome, country)}</strong>
          </div>
          <div>
            <div style={{ color: 'var(--text-muted)' }}>Last Tax</div>
            <strong style={{ color: '#FBBF24' }}>-{formatMoney(finance.lastTurnTax, country)}</strong>
          </div>
          <div>
            <div style={{ color: 'var(--text-muted)' }}>Expenses</div>
            <strong style={{ color: '#FB7185' }}>-{formatMoney(finance.lastTurnExpenses, country)}</strong>
          </div>
        </div>
      </div>

      {/* Credit Score & Debts */}
      <div className="card">
        <div className="section-header">
          <span className="section-title" style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
            <CreditCard size={13} /> Credit Score & Debt
          </span>
          <strong className="tabular" style={{ fontSize: 14, color: finance.creditScore >= 700 ? '#34D399' : '#FBBF24' }}>
            {finance.creditScore} / 850
          </strong>
        </div>
        <div className="bar-track" style={{ marginBottom: 10 }}>
          <div
            className="bar-fill"
            style={{
              width: `${creditPct}%`,
              backgroundColor: finance.creditScore >= 700 ? '#10B981' : finance.creditScore >= 580 ? '#F59E0B' : '#F43F5E',
            }}
          />
        </div>
        <div className="tabular" style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, color: 'var(--text-secondary)' }}>
          <span>Cash: <strong style={{ color: 'var(--text-primary)' }}>{formatMoney(finance.cash, country)}</strong></span>
          <span>Loans: <strong style={{ color: 'var(--accent-rose)' }}>{formatMoney(education.studentLoanBalance + finance.mortgageBalance, country)}</strong></span>
        </div>
      </div>

      {/* Investments */}
      <div className="card">
        <div className="section-header">
          <span className="section-title" style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
            <TrendingUp size={13} /> Investments (1 AP)
          </span>
          {!isLegalAdult ? (
            <span className="chip chip-amber">
              <Lock size={11} /> Age {country.legalAdultAge}+
            </span>
          ) : (
            <div style={{ display: 'flex', gap: 4 }}>
              {[500, 1000, 5000, 25000].map((amt) => (
                <button
                  key={amt}
                  className={`chip tabular ${investAmount === amt ? 'chip-emerald' : ''}`}
                  onClick={() => setInvestAmount(amt)}
                >
                  {amt >= 1000 ? `${amt / 1000}k` : amt}
                </button>
              ))}
            </div>
          )}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {pack.assets.investments.map((asset) => {
            const holding = finance.investments.find((h) => h.assetId === asset.id);
            const balance = holding?.balance ?? 0;

            return (
              <div
                key={asset.id}
                style={{
                  padding: '9px 10px',
                  borderRadius: 9,
                  backgroundColor: 'var(--bg-input)',
                  border: '1px solid var(--border-subtle)',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 6,
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <div style={{ fontSize: 13, fontWeight: 600 }}>{asset.name}</div>
                    <div className="tabular" style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
                      Exp. {Math.round(asset.expectedAnnualReturn * 100)}%/yr • Vol {Math.round(asset.annualVolatility * 100)}%
                    </div>
                  </div>
                  <div className="tabular" style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: 13, fontWeight: 700, color: balance > 0 ? '#34D399' : 'var(--text-muted)' }}>
                      {formatMoney(balance, country)}
                    </div>
                  </div>
                </div>

                <div style={{ display: 'flex', gap: 6 }}>
                  <button
                    className="btn btn-sm btn-primary"
                    style={{ flex: 1 }}
                    disabled={!isLegalAdult || finance.cash < investAmount}
                    onClick={() => onAction({ type: 'INVEST', assetId: asset.id, amount: investAmount })}
                  >
                    Buy {formatMoney(investAmount, country)}
                  </button>
                  <button
                    className="btn btn-sm"
                    style={{ flex: 1 }}
                    disabled={!isLegalAdult || balance <= 0}
                    onClick={() =>
                      onAction({
                        type: 'SELL_INVESTMENT',
                        assetId: asset.id,
                        amount: Math.min(balance, investAmount),
                      })
                    }
                  >
                    Sell
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Housing */}
      <div className="card">
        <div className="section-header">
          <span className="section-title" style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
            <Home size={13} /> Housing & Real Estate (2 AP)
          </span>
          {!isLegalAdult && (
            <span className="chip chip-amber">
              <Lock size={11} /> Move out at {country.legalAdultAge}y
            </span>
          )}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {(() => {
            const currentHome = finance.ownsHousing
              ? pack.assets.housing.find((x) => x.id === finance.housingId)
              : null;
            const currentEquity = currentHome
              ? Math.round(currentHome.purchasePrice * country.costOfLivingMultiplier) - finance.mortgageBalance
              : 0;
            const buyingPower = finance.cash + currentEquity;

            return pack.assets.housing.map((h) => {
              const isCurrent = finance.housingId === h.id;
              const isOwned = isCurrent && finance.ownsHousing;
              const rent = Math.round(h.annualRent * country.costOfLivingMultiplier);
              const price = Math.round(h.purchasePrice * country.costOfLivingMultiplier);
              const downPayment = Math.round(price * 0.2);
              const showRent = !isCurrent;
              const showBuy = price > 0 && !isOwned;

              return (
                <div
                  key={h.id}
                  style={{
                    padding: '9px 10px',
                    borderRadius: 9,
                    backgroundColor: 'var(--bg-input)',
                    border: `1px solid ${isCurrent ? 'var(--accent-emerald)' : 'var(--border-subtle)'}`,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 6,
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                      <div style={{ fontSize: 13, fontWeight: 600, display: 'flex', alignItems: 'center', gap: 6 }}>
                        <Building2 size={13} color="#94A3B8" />
                        {h.name}
                        {isCurrent && (
                          <span className="chip chip-emerald" style={{ fontSize: 9.5 }}>
                            {finance.ownsHousing ? 'Owned' : 'Living Here'}
                          </span>
                        )}
                      </div>
                      <div className="tabular" style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 2 }}>
                        {rent === 0 ? 'Free' : `Rent ${formatMoney(rent, country)}/yr`}
                        {price > 0 && ` • Buy ${formatMoney(price, country)}`}
                      </div>
                    </div>
                  </div>

                  {(showRent || showBuy) && (
                    <div style={{ display: 'flex', gap: 6 }}>
                      {showRent && (
                        <button
                          className="btn btn-sm"
                          style={{ flex: 1 }}
                          disabled={!isLegalAdult && h.id !== 'parents_home'}
                          onClick={() => onAction({ type: 'CHANGE_HOUSING', housingId: h.id, buy: false })}
                        >
                          {rent === 0 ? 'Move In' : 'Rent'}
                        </button>
                      )}
                      {showBuy && (
                        <button
                          className="btn btn-sm btn-primary"
                          style={{ flex: 1 }}
                          disabled={!isLegalAdult || buyingPower < downPayment}
                          onClick={() => onAction({ type: 'CHANGE_HOUSING', housingId: h.id, buy: true })}
                        >
                          Buy (20%: {formatMoney(downPayment, country)})
                        </button>
                      )}
                    </div>
                  )}
                </div>
              );
            });
          })()}
        </div>
      </div>
    </div>
  );
};
