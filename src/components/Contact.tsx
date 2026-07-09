import { motion } from 'framer-motion';
import { Phone, Mail, MapPin, ArrowUpRight } from 'lucide-react';
import { openWhatsApp, waLink } from '../lib/whatsapp';

const WA_MSG = "Hello MIROIR Studio, I'd like to enquire about your services.";

export default function Contact() {
  return (
    <section id="contact" className="section-pad bg-[#080604] relative">
      <div className="absolute top-0 inset-x-0 h-px bg-gradient-to-r from-transparent via-[#d4a84b20] to-transparent" />
      <div className="container-site">
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.8 }}
          className="mb-16 text-center"
        >
          <div className="flex items-center justify-center gap-4 mb-6">
            <div className="w-6 h-px bg-[#d4a84b]" />
            <span className="font-sans text-[0.52rem] tracking-[0.55em] text-[#d4a84b] uppercase font-medium">Contact</span>
            <div className="w-6 h-px bg-[#d4a84b]" />
          </div>
          <h2 className="font-sans text-5xl md:text-6xl text-[#f0ebe0] font-bold">Let's create together</h2>
          <div className="gold-rule mt-8" />
        </motion.div>

        <div className="max-w-xl mx-auto text-center mb-16">
          <motion.p
            initial={{ opacity: 0, y: 12 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.8, delay: 0.1 }}
            className="font-body text-[0.82rem] text-white/34 leading-[1.9] mb-10"
          >
            Ready to elevate your brand visuals? Reach out and let's discuss your project.
          </motion.p>
          <motion.div
            initial={{ opacity: 0, y: 12 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.8, delay: 0.2 }}
          >
            <a
              href={waLink(WA_MSG)}
              onClick={e => { e.preventDefault(); openWhatsApp(WA_MSG); }}
              className="wa-pulse inline-flex items-center gap-2 bg-[#d4a84b] text-black font-sans text-[0.62rem] tracking-[0.24em] uppercase px-10 sm:px-12 py-5 hover:bg-[#d4b47a] transition-all duration-400 cursor-pointer font-medium"
            >
              <Phone size={14} />
              Book via WhatsApp
            </a>
          </motion.div>
        </div>

        <div className="gold-rule mb-14" />

        <motion.div
          initial={{ opacity: 0, y: 14 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.8, delay: 0.3 }}
          className="grid grid-cols-1 sm:grid-cols-3 gap-3"
        >
          {[
            { icon: <ArrowUpRight size={15} />, label: 'Instagram', value: '@miroirstudio.ae', onClick: () => window.open('https://www.instagram.com/miroirstudio.ae/', '_blank', 'noopener,noreferrer') },
            { icon: <Mail size={15} />, label: 'Email', value: 'miroirstudioae@gmail.com', onClick: () => { window.location.href = 'mailto:miroirstudioae@gmail.com' } },
            { icon: <MapPin size={15} />, label: 'Location', value: 'Abu Dhabi · Dubai', onClick: undefined },
          ].map(item => (
            <div
              key={item.label}
              onClick={item.onClick}
              className={`flex flex-col items-center gap-3 py-7 px-4 border border-white/[0.06] hover:border-[#d4a84b]/20 transition-colors duration-400 ${item.onClick ? 'cursor-pointer' : ''}`}
              style={{ background: 'rgba(255,255,255,0.008)' }}
            >
              <span className="text-[#d4a84b]/50">{item.icon}</span>
              <div className="text-center">
                <p className="eyebrow text-white/30 text-[0.48rem] mb-1.5">{item.label}</p>
                <p className="font-body text-[0.78rem] text-white/55 font-normal">{item.value}</p>
              </div>
            </div>
          ))}
        </motion.div>
      </div>
    </section>
  );
}
