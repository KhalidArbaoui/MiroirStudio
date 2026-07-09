import { openWhatsApp, waLink } from '../lib/whatsapp';

const WA = "Hello MIROIR Studio, I'd like to enquire about your services.";

export default function Footer() {
  return (
    <footer className="border-t border-white/[0.04] bg-[#070503]">
      <div className="container-site py-9 flex flex-col md:flex-row items-center justify-between gap-5">
        <a href="#home" className="flex flex-col items-center md:items-start leading-none group">
          <span className="font-monea text-[1.1rem] tracking-[0.3em] text-[#d4a84b]/50 group-hover:text-[#d4a84b]/70 transition-colors duration-300">MIROIR</span>
          <span className="font-sans text-[0.42rem] tracking-[0.42em] text-white/22 uppercase mt-0.5 font-medium">Studio</span>
        </a>
        <p className="font-sans text-[0.52rem] tracking-[0.2em] text-white/26 uppercase font-medium">
          &copy; {new Date().getFullYear()} MIROIR Studio &nbsp;·&nbsp; UAE
        </p>
        <div className="flex items-center gap-7">
          {[
            { label: 'Instagram', fn: () => window.open('https://www.instagram.com/miroirstudio.ae/', '_blank', 'noopener,noreferrer') },
            { label: 'WhatsApp', fn: () => openWhatsApp(WA) },
            { label: 'Email', fn: () => { window.location.href = 'mailto:miroirstudioae@gmail.com' } },
          ].map(item => (
            <button
              key={item.label}
              onClick={item.fn}
              className="font-sans text-[0.52rem] tracking-[0.2em] text-white/30 hover:text-[#d4a84b]/75 uppercase transition-colors duration-300 cursor-pointer bg-transparent border-none font-medium"
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>
    </footer>
  );
}
