import { useLayoutEffect } from 'react';
import Navbar from './components/Navbar';
import Hero from './components/Hero';
import Portfolio from './components/Portfolio';
import VisualStrip from './components/VisualStrip';
import Services from './components/Services';
import About from './components/About';
import Testimonials from './components/Testimonials';
import Contact from './components/Contact';
import Footer from './components/Footer';

function HashScroll() {
  useLayoutEffect(() => {
    let timers: number[] = [];
    const previousRestoration = window.history.scrollRestoration;
    window.history.scrollRestoration = 'manual';

    const scrollToCurrentHash = (delays: number[]) => {
      const id = decodeURIComponent(window.location.hash.replace('#', ''));
      if (!id) return;

      timers.forEach(timer => window.clearTimeout(timer));
      timers = delays.map(delay => window.setTimeout(() => {
        const target = document.getElementById(id);
        if (!target) return;

        const top = Math.max(0, target.getBoundingClientRect().top + window.scrollY - 72);
        const scrollingElement = document.scrollingElement || document.documentElement;
        scrollingElement.scrollTop = top;
        document.documentElement.scrollTop = top;
        document.body.scrollTop = top;
        window.scrollTo(0, top);
      }, delay));
    };

    const scrollOnHashChange = () => scrollToCurrentHash([0, 80, 220, 500]);
    const scrollOnInitialLoad = () => scrollToCurrentHash([0, 80, 220, 500, 1000, 1800, 2600]);

    scrollOnInitialLoad();
    window.addEventListener('hashchange', scrollOnHashChange);
    window.addEventListener('pageshow', scrollOnInitialLoad);

    return () => {
      timers.forEach(timer => window.clearTimeout(timer));
      window.history.scrollRestoration = previousRestoration;
      window.removeEventListener('hashchange', scrollOnHashChange);
      window.removeEventListener('pageshow', scrollOnInitialLoad);
    };
  }, []);

  return null;
}

export default function App() {
  return (
    <div className="bg-[#080808] min-h-screen overflow-x-hidden">
      <HashScroll />
      <Navbar />
      <Hero />
      <Portfolio />
      <VisualStrip />
      <Services />
      <About />
      <Testimonials />
      <Contact />
      <Footer />
    </div>
  );
}
