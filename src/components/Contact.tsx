import { useState } from 'react';
import { motion } from 'framer-motion';
import { Send, ArrowUpRight, Mail, MapPin } from 'lucide-react';

const TO = 'miroirstudioae@gmail.com';

const SERVICES = [
  'Food Photography',
  'Product Photography',
  'Reels & Video Content',
  'Perfume & Oud',
  'Cosmetics',
  'Jewelry',
  'Other',
];

export default function Contact() {
  const [form, setForm] = useState({ name: '', phone: '', email: '', service: '', message: '' });

  const update = (field: string, value: string) => setForm(prev => ({ ...prev, [field]: value }));

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const subject = encodeURIComponent('New Enquiry — MIROIR Studio');
    const body = encodeURIComponent([
      `Name: ${form.name}`,
      form.phone ? `Phone: ${form.phone}` : '',
      form.email ? `Email: ${form.email}` : '',
      form.service ? `Service: ${form.service}` : '',
      form.message ? `Message: ${form.message}` : '',
    ].filter(Boolean).join('\n'));
    window.location.href = `mailto:${TO}?subject=${subject}&body=${body}`;
  };

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
          <p className="font-body text-[0.82rem] text-white/34 leading-[1.9] mt-6 max-w-md mx-auto">
            Fill in the form below and we'll get back to you shortly.
          </p>
          <div className="gold-rule mt-8" />
        </motion.div>

        <motion.form
          onSubmit={handleSubmit}
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.8, delay: 0.15 }}
          className="max-w-xl mx-auto flex flex-col gap-5 mb-16"
        >
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
            <div className="flex flex-col gap-1.5">
              <label className="font-sans text-[0.52rem] tracking-[0.3em] text-[#d4a84b]/60 uppercase font-medium">Name *</label>
              <input
                required
                type="text"
                value={form.name}
                onChange={e => update('name', e.target.value)}
                placeholder="Your name"
                className="bg-[#0c0a08] border border-[#24201a] text-[#f0ebe0] font-body text-[0.82rem] px-4 py-3.5 outline-none focus:border-[#d4a84b40] transition-colors duration-300 placeholder:text-white/20"
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="font-sans text-[0.52rem] tracking-[0.3em] text-[#d4a84b]/60 uppercase font-medium">Phone</label>
              <input
                type="tel"
                value={form.phone}
                onChange={e => update('phone', e.target.value)}
                placeholder="+971..."
                className="bg-[#0c0a08] border border-[#24201a] text-[#f0ebe0] font-body text-[0.82rem] px-4 py-3.5 outline-none focus:border-[#d4a84b40] transition-colors duration-300 placeholder:text-white/20"
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
            <div className="flex flex-col gap-1.5">
              <label className="font-sans text-[0.52rem] tracking-[0.3em] text-[#d4a84b]/60 uppercase font-medium">Email</label>
              <input
                type="email"
                value={form.email}
                onChange={e => update('email', e.target.value)}
                placeholder="you@email.com"
                className="bg-[#0c0a08] border border-[#24201a] text-[#f0ebe0] font-body text-[0.82rem] px-4 py-3.5 outline-none focus:border-[#d4a84b40] transition-colors duration-300 placeholder:text-white/20"
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="font-sans text-[0.52rem] tracking-[0.3em] text-[#d4a84b]/60 uppercase font-medium">Service</label>
              <select
                value={form.service}
                onChange={e => update('service', e.target.value)}
                className="bg-[#0c0a08] border border-[#24201a] text-[#f0ebe0] font-body text-[0.82rem] px-4 py-3.5 outline-none focus:border-[#d4a84b40] transition-colors duration-300 appearance-none cursor-pointer"
                style={{ backgroundImage: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%23d4a84b' stroke-width='2'%3E%3Cpolyline points='6 9 12 15 18 9'/%3E%3C/svg%3E")`, backgroundRepeat: 'no-repeat', backgroundPosition: 'right 16px center' }}
              >
                <option value="">Select service...</option>
                {SERVICES.map(s => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
            </div>
          </div>

          <div className="flex flex-col gap-1.5">
            <label className="font-sans text-[0.52rem] tracking-[0.3em] text-[#d4a84b]/60 uppercase font-medium">Message</label>
            <textarea
              value={form.message}
              onChange={e => update('message', e.target.value)}
              placeholder="Tell us about your project..."
              rows={4}
              className="bg-[#0c0a08] border border-[#24201a] text-[#f0ebe0] font-body text-[0.82rem] px-4 py-3.5 outline-none focus:border-[#d4a84b40] transition-colors duration-300 resize-none placeholder:text-white/20"
            />
          </div>

          <button
            type="submit"
            className="inline-flex items-center justify-center gap-2 bg-[#d4a84b] text-black font-sans text-[0.62rem] tracking-[0.24em] uppercase px-10 py-4 hover:bg-[#d4b47a] transition-all duration-300 cursor-pointer font-medium mt-1"
          >
            <Send size={13} />
            Send Enquiry
          </button>
        </motion.form>

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
