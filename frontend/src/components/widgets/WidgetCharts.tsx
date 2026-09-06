import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { useChartPalette, readToken } from '../../hooks/useChartPalette'
import { formatCompactCurrency, formatCurrency } from '../../lib/format'
import type { AnalyticsPoint } from '../../api/types'

/**
 * Chart primitives shared by the dashboard widgets.
 *
 * Deliberately plain: axes, a grid, a tooltip, and no entry animation. The job
 * of a spending chart is to be read accurately, not to be impressive, and
 * Recharts' default 1.5s reveal delays the number the user came for. Turning it
 * off also makes screenshots and tests deterministic - a half-drawn line looks
 * exactly like missing data.
 */

const CHART_HEIGHT = 240

interface ChartProps {
  points: AnalyticsPoint[]
  currency: string
  showLegend?: boolean
}

function useAxisStyle() {
  return {
    stroke: readToken('--text-subtle', '#8a939e'),
    grid: readToken('--border', '#e3e6ea'),
    fontSize: 12,
  }
}

function tooltipStyle() {
  return {
    background: readToken('--surface-raised', '#ffffff'),
    border: `1px solid ${readToken('--border', '#e3e6ea')}`,
    borderRadius: 8,
    color: readToken('--text', '#14181d'),
    fontSize: 13,
  }
}

export function SpendingLineChart({ points, currency }: ChartProps) {
  const palette = useChartPalette()
  const axis = useAxisStyle()

  return (
    <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
      <LineChart data={points} margin={{ top: 8, right: 8, bottom: 4, left: 4 }}>
        <CartesianGrid stroke={axis.grid} vertical={false} />
        <XAxis
          dataKey="label"
          stroke={axis.stroke}
          fontSize={axis.fontSize}
          tickLine={false}
          axisLine={{ stroke: axis.grid }}
          minTickGap={24}
        />
        <YAxis
          stroke={axis.stroke}
          fontSize={axis.fontSize}
          tickLine={false}
          axisLine={false}
          width={64}
          tickFormatter={(value: number) => formatCompactCurrency(value, currency)}
        />
        <Tooltip
          contentStyle={tooltipStyle()}
          formatter={(value: number) => [formatCurrency(value, currency), 'Spending']}
        />
        <Line
          type="monotone"
          dataKey="value"
          stroke={palette[0]}
          strokeWidth={2}
          dot={false}
          activeDot={{ r: 4 }}
          isAnimationActive={false}
        />
      </LineChart>
    </ResponsiveContainer>
  )
}

export function SpendingBarChart({ points, currency }: ChartProps) {
  const palette = useChartPalette()
  const axis = useAxisStyle()

  return (
    <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
      <BarChart data={points} margin={{ top: 8, right: 8, bottom: 4, left: 4 }}>
        <CartesianGrid stroke={axis.grid} vertical={false} />
        <XAxis
          dataKey="label"
          stroke={axis.stroke}
          fontSize={axis.fontSize}
          tickLine={false}
          axisLine={{ stroke: axis.grid }}
          interval={0}
          angle={points.length > 6 ? -30 : 0}
          textAnchor={points.length > 6 ? 'end' : 'middle'}
          height={points.length > 6 ? 60 : 30}
        />
        <YAxis
          stroke={axis.stroke}
          fontSize={axis.fontSize}
          tickLine={false}
          axisLine={false}
          width={64}
          tickFormatter={(value: number) => formatCompactCurrency(value, currency)}
        />
        <Tooltip
          cursor={{ fill: axis.grid, opacity: 0.4 }}
          contentStyle={tooltipStyle()}
          formatter={(value: number) => [formatCurrency(value, currency), 'Spending']}
        />
        <Bar dataKey="value" radius={[4, 4, 0, 0]} isAnimationActive={false}>
          {points.map((point, index) => (
            <Cell key={point.key} fill={palette[index % palette.length]} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  )
}

export function SpendingPieChart({ points, currency, donut = false, showLegend = true }: ChartProps & { donut?: boolean }) {
  const palette = useChartPalette()

  return (
    <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
      <PieChart>
        <Pie
          data={points}
          dataKey="value"
          nameKey="label"
          innerRadius={donut ? 52 : 0}
          outerRadius={80}
          paddingAngle={1}
          isAnimationActive={false}
        >
          {points.map((point, index) => (
            <Cell key={point.key} fill={palette[index % palette.length]} />
          ))}
        </Pie>
        <Tooltip
          contentStyle={tooltipStyle()}
          formatter={(value: number, name: string) => [formatCurrency(value, currency), name]}
        />
        {showLegend ? <Legend verticalAlign="bottom" height={36} iconSize={9} /> : null}
      </PieChart>
    </ResponsiveContainer>
  )
}

/** Ranked list, used for "top merchants" style widgets. */
export function RankedList({ points, currency }: ChartProps) {
  return (
    <ol className="ranked-list">
      {points.map((point, index) => (
        <li key={point.key}>
          <span className="ranked-list__label">
            <span className="muted">{index + 1}.</span> {point.label}
          </span>
          <span className="numeric">{formatCurrency(point.value, currency)}</span>
        </li>
      ))}
    </ol>
  )
}
