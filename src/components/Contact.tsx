import { motion } from 'framer-motion';
import { MessageCircle, Mail, MapPin } from 'lucide-react';
import { openWhatsApp, waLink } from '../lib/whatsapp';

const WA_MSG = "Hello MIROIR Studio, I'd like to discuss a project.";

function InstagramIcon({ size = 15 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="3" y="3" width="18" height="18" rx="5" stroke="currentColor" strokeWidth="1.7" />
      <circle cx="12" cy="12" r="4" stroke="currentColor" strokeWidth="1.7" />
      <circle cx="17" cy="7" r="1.1" fill="currentColor" />
    </svg>
  );
}

export default function Contact() {
  return (
    <section id="contact" className="section-pad bg-[#080808] relative overflow-hidden">
      <div
        className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] rounded-full pointer-events-none"
        style={{ background: 'radial-gradient(circle, rgba(201,169,110,0.03) 0%, transparent 70%)' }}
      />

      <div className="container-site">
        <div className="max-w-xl mx-auto text-center">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.9 }}
          >
            <span className="eyebrow text-[#c9a96e]/60 block mb-8">Get In Touch</span>

            <h2 className="font-serif text-5xl md:text-6xl lg:text-7xl text-white/80 italic leading-[1.05] mb-6">
              Let&apos;s create<br />
              <span className="text-[#c9a96e]/80">something</span><br />
              extraordinary.
            </h2>

            <p className="font-sans text-[0.82rem] text-white/42 leading-[1.85] max-w-xs mx-auto mt-7 mb-14">
              Ready to elevate your brand visuals? Reach out and let&apos;s discuss your project.
            </p>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 14 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.8, delay: 0.2 }}
            className="mb-18"
          >
            <a
              href={waLink(WA_MSG)}
              onClick={e => { e.preventDefault(); openWhatsApp(WA_MSG); }}
              className="wa-pulse inline-flex items-center gap-3 bg-[#c9a96e] text-black
                font-sans text-[0.64rem] tracking-[0.24em] uppercase px-10 sm:px-12 py-5
                hover:bg-[#d4b47a] transition-all duration-400 cursor-pointer"
            >
              <MessageCircle size={14} />
              Book via WhatsApp
            </a>
          </motion.div>

          <div className="gold-rule mb-14" />

          <motion.div
            initial={{ opacity: 0, y: 14 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.8, delay: 0.3 }}
            className="grid grid-cols-1 sm:grid-cols-3 gap-3"
          >
            {[
              {
                icon: <InstagramIcon size={15} />,
                label: 'Instagram',
                value: '@miroirstudio.ae',
                onClick: () => window.open('https://www.instagram.com/miroirstudio.ae/', '_blank', 'noopener,noreferrer'),
              },
              {
                icon: <Mail size={15} />,
                label: 'Email',
                value: 'miroirstudioae@gmail.com',
                onClick: () => { window.location.href = 'mailto:miroirstudioae@gmail.com'; },
              },
              {
                icon: <MapPin size={15} />,
                label: 'Location',
                value: 'Abu Dhabi · Dubai',
                onClick: undefined,
              },
            ].map(item => (
              <div
                key={item.label}
                onClick={item.onClick}
                className={`flex flex-col items-center gap-3 py-7 px-4
                  border border-white/[0.06] hover:border-[#c9a96e]/20
                  transition-colors duration-400
                  ${ item.onClick ? 'cursor-pointer' : '' }`}
                style={{ background: 'rgba(255,255,255,0.008)' }}
              >
                <span className="text-[#c9a96e]/50">{item.icon}</span>
                <div className="text-center">
                  <p className="eyebrow text-white/30 text-[0.48rem] mb-1.5">{item.label}</p>
                  <p className="font-sans text-[0.78rem] text-white/55">{item.value}</p>
                </div>
              </div>
            ))}
          </motion.div>
        </div>
      </div>
    </section>
  );
}
