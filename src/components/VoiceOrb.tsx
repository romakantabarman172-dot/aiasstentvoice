import { useEffect, useRef } from 'react';
import { Mic, Square } from 'lucide-react';

interface VoiceOrbProps {
  isListening: boolean;
  isThinking: boolean;
  audioLevel: number;
  onClick: () => void;
}

export function VoiceOrb({
  isListening,
  isThinking,
  audioLevel,
  onClick,
}: VoiceOrbProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const stateRef = useRef({ isListening, isThinking, audioLevel });

  useEffect(() => {
    stateRef.current = { isListening, isThinking, audioLevel };
  }, [isListening, isThinking, audioLevel]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animId = 0;
    let phase = 0;
    let smoothLevel = 0;

    const render = () => {
      const { isListening: listening, isThinking: thinking, audioLevel: level } = stateRef.current;
      const dpr = window.devicePixelRatio || 1;
      const size = 104;
      if (canvas.width !== size * dpr) {
        canvas.width = size * dpr;
        canvas.height = size * dpr;
      }

      ctx.save();
      ctx.scale(dpr, dpr);
      ctx.clearRect(0, 0, size, size);

      const cx = size / 2;
      const cy = size / 2;
      const targetLevel = listening ? Math.max(0.12, Math.min(1, level)) : 0;
      smoothLevel += (targetLevel - smoothLevel) * 0.18;
      phase += listening ? 0.06 : thinking ? 0.045 : 0.02;

      const baseR = 26;
      const breathe = 1 + Math.sin(phase * 1.4) * 0.04;

      // Ambient radial aura
      const haloR = baseR * (1.55 + smoothLevel * 0.45) * breathe;
      const haloGrad = ctx.createRadialGradient(cx, cy, baseR * 0.4, cx, cy, haloR);
      haloGrad.addColorStop(0, listening ? 'rgba(76, 125, 255, 0.42)' : 'rgba(76, 125, 255, 0.22)');
      haloGrad.addColorStop(0.55, listening ? 'rgba(123, 108, 255, 0.22)' : 'rgba(123, 108, 255, 0.08)');
      haloGrad.addColorStop(1, 'rgba(10, 13, 20, 0)');
      ctx.fillStyle = haloGrad;
      ctx.beginPath();
      ctx.arc(cx, cy, haloR, 0, Math.PI * 2);
      ctx.fill();

      // Organic harmonic rings
      const layers = listening ? 3 : 2;
      const ringColors = [
        listening ? 'rgba(37, 194, 227, 0.78)' : 'rgba(37, 194, 227, 0.34)',
        listening ? 'rgba(123, 108, 255, 0.65)' : 'rgba(123, 108, 255, 0.26)',
        'rgba(76, 125, 255, 0.52)',
      ];

      for (let layer = 0; layer < layers; layer++) {
        ctx.beginPath();
        const steps = 64;
        const amp = listening ? (3.5 + smoothLevel * 8.5) * (1 - layer * 0.22) : 1.6;
        const freq = 3 + layer;
        const layerPhase = phase * (layer % 2 === 0 ? 1 : -1.25) + layer * 1.1;
        const rBase = baseR * (1.16 + layer * 0.09);

        for (let i = 0; i <= steps; i++) {
          const theta = (i / steps) * Math.PI * 2;
          const r = rBase + Math.sin(theta * freq + layerPhase) * amp;
          const x = cx + Math.cos(theta) * r;
          const y = cy + Math.sin(theta) * r;
          if (i === 0) ctx.moveTo(x, y);
          else ctx.lineTo(x, y);
        }
        ctx.closePath();
        ctx.strokeStyle = ringColors[layer];
        ctx.lineWidth = listening ? 2 : 1.3;
        ctx.stroke();
      }

      // Thinking orbital sweep arc
      if (thinking) {
        ctx.beginPath();
        ctx.arc(cx, cy, baseR * 1.36, phase * 1.8, phase * 1.8 + Math.PI * 1.15);
        ctx.strokeStyle = '#25C2E3';
        ctx.lineWidth = 2.6;
        ctx.lineCap = 'round';
        ctx.stroke();
      }

      // Core gradient sphere
      const coreR = baseR * (listening ? 0.96 + smoothLevel * 0.14 : 0.94 * breathe);
      const coreGrad = ctx.createLinearGradient(cx - coreR, cy - coreR, cx + coreR, cy + coreR);
      coreGrad.addColorStop(0, '#25C2E3');
      coreGrad.addColorStop(0.5, '#4C7DFF');
      coreGrad.addColorStop(1, '#7B6CFF');

      ctx.fillStyle = coreGrad;
      ctx.beginPath();
      ctx.arc(cx, cy, coreR, 0, Math.PI * 2);
      ctx.fill();

      ctx.restore();
      animId = requestAnimationFrame(render);
    };

    animId = requestAnimationFrame(render);
    return () => cancelAnimationFrame(animId);
  }, []);

  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={isListening ? 'Stop voice capture' : 'Tap to speak with Aura'}
      className="relative flex items-center justify-center w-[104px] h-[104px] rounded-full cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#25C2E3] transition-transform active:scale-95"
    >
      <canvas
        ref={canvasRef}
        className="w-[104px] h-[104px] pointer-events-none"
      />
      <span className="absolute inset-0 flex items-center justify-center pointer-events-none text-white">
        {isListening ? <Square className="w-5 h-5 fill-white" /> : <Mic className="w-6 h-6" />}
      </span>
    </button>
  );
}
