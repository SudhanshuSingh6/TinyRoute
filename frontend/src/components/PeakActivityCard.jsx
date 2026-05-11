import { formatPeakLabel, getPeakTitle, toNumber } from "./analyticsHelper"

const PeakActivityCard = ({ peakActivity }) => {
  const type = peakActivity?.type;
  const label = peakActivity?.label;
  const count = peakActivity?.count;

  return (
    <div
      className="rounded-xl p-6 flex flex-col items-center justify-center text-center gap-3"
      style={{ background: "linear-gradient(135deg, #3b82f6 0%, #9333ea 100%)" }}
    >
      <div
        className="w-14 h-14 rounded-full flex items-center justify-center text-2xl mb-1"
        style={{
          background: "rgba(255,255,255,0.15)",
          border: "1px solid rgba(255,255,255,0.25)",
        }}
      >
        ⏱
      </div>

      <p className="text-xs font-semibold uppercase tracking-widest text-blue-100">
        {type}
      </p>

      <p className="text-4xl font-bold text-white font-montserrat leading-none">
        {label}
      </p>

      <p className="text-sm text-blue-100 leading-relaxed">
        Highest volume with{" "}
        <span className="font-bold text-white">{toNumber(count)} clicks</span>{" "}
        during this window.
      </p>
    </div>
  );
};

export default PeakActivityCard;